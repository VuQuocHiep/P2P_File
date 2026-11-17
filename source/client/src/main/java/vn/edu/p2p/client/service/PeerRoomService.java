package vn.edu.p2p.client.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.stereotype.Service;
import vn.edu.p2p.client.dto.request.*;
import vn.edu.p2p.client.dto.response.*;
import vn.edu.p2p.client.model.Entry;

/** Peer-side proxy for room APIs; the tracker owns membership and access rules. */
@Service
public class PeerRoomService {
    private final PeerContext context;
    private final PeerLibraryService libraryService;
    private final ObjectMapper json;

    public PeerRoomService(
            PeerContext context, PeerLibraryService libraryService, ObjectMapper json) {
        this.context = context;
        this.libraryService = libraryService;
        this.json = json;
    }

    public List<RoomView> rooms() throws Exception {
        return views(context.tracker.call("GET", "/api/rooms", null));
    }

    public RoomView create(RoomCreate body) throws Exception {
        return view(context.tracker.call("POST", "/api/rooms", body));
    }

    public RoomView join(String roomId, RoomJoin body) throws Exception {
        return view(context.tracker.call("POST", "/api/rooms/" + id(roomId) + "/join", body));
    }

    public List<RoomMemberView> requests(String roomId) throws Exception {
        return members(context.tracker.call("GET", "/api/rooms/" + id(roomId) + "/requests", null));
    }

    public RoomMemberView decide(String roomId, String username, RoomDecision body)
            throws Exception {
        return json.treeToValue(
                context.tracker.call(
                        "PATCH",
                        "/api/rooms/" + id(roomId) + "/requests/" + encode(username),
                        body),
                RoomMemberView.class);
    }

    public List<RoomMemberView> members(String roomId) throws Exception {
        return members(context.tracker.call("GET", "/api/rooms/" + id(roomId) + "/members", null));
    }

    public List<FileResponse> files(String roomId) throws Exception {
        return json.readerForListOf(FileResponse.class)
                .readValue(context.tracker.call("GET", "/api/rooms/" + id(roomId) + "/files", null));
    }

    public RoomView update(String roomId, RoomUpdate body) throws Exception {
        return view(context.tracker.call("PATCH", "/api/rooms/" + id(roomId), body));
    }

    public MessageResponse delete(String roomId) throws Exception {
        return json.treeToValue(
                context.tracker.call("DELETE", "/api/rooms/" + id(roomId), null),
                MessageResponse.class);
    }

    public MessageResponse removeMember(String roomId, String username) throws Exception {
        return json.treeToValue(
                context.tracker.call(
                        "DELETE",
                        "/api/rooms/" + id(roomId) + "/members/" + encode(username),
                        null),
                MessageResponse.class);
    }

    /** Moves one library file into a room and re-advertises it with the room scope. */
    public MessageResponse share(String roomId, String fileId) throws Exception {
        String room = id(roomId);
        String previous;
        synchronized (context.storage.monitor()) {
            Entry entry = context.library.findByFileId(fileId);
            if (entry == null) throw new IllegalArgumentException("File không có trong thư viện.");
            if (!entry.sharing) throw new IllegalArgumentException("Bật chia sẻ file trước khi thêm vào phòng.");
            previous = entry.roomId;
            entry.roomId = room;
            context.storage.saveCheckpoint();
        }
        try {
            libraryService.advertise(fileId);
        } catch (Exception | Error failure) {
            synchronized (context.storage.monitor()) {
                Entry entry = context.library.findByFileId(fileId);
                if (entry != null) {
                    entry.roomId = previous;
                    context.storage.saveCheckpoint();
                }
            }
            throw failure;
        }
        return new MessageResponse("Đã thêm file vào phòng. File mới cần quản trị viên duyệt.");
    }

    private List<RoomView> views(JsonNode data) throws Exception {
        return json.readerForListOf(RoomView.class).readValue(data);
    }

    private RoomView view(JsonNode data) throws Exception {
        return json.treeToValue(data, RoomView.class);
    }

    private List<RoomMemberView> members(JsonNode data) throws Exception {
        return json.readerForListOf(RoomMemberView.class).readValue(data);
    }

    private static String id(String roomId) {
        if (roomId == null || !roomId.matches("[a-zA-Z0-9][a-zA-Z0-9_-]{2,23}"))
            throw new IllegalArgumentException("Mã phòng không hợp lệ.");
        return roomId;
    }

    private static String encode(String value) {
        return java.net.URLEncoder.encode(value == null ? "" : value, java.nio.charset.StandardCharsets.UTF_8);
    }
}
