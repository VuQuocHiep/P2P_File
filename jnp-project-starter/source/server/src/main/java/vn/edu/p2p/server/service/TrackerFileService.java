package vn.edu.p2p.server.service;

import vn.edu.p2p.server.dto.*;
import vn.edu.p2p.common.model.Manifest;
import vn.edu.p2p.server.model.*;
import java.util.*;

/** Registry operations invoked under the TrackerService monitor. */
final class TrackerFileService {
    private final TrackerContext context;
    private final TrackerAccess access;
    TrackerFileService(TrackerContext context, TrackerAccess access) {
        this.context = context;
        this.access = access;
    }
    Map<String, Object> advertise(String token, Advertisement input) {
        Account a = access.account(token, true); Peer p = access.ownPeer(a, input.peerId(), true);
        access.require(input.manifest() != null, "Thiếu manifest."); input.manifest().validate();
        access.require(input.chunks() != null && input.chunks().size() <= input.manifest().hashes().size()
                && new HashSet<>(input.chunks()).size() == input.chunks().size()
                && input.chunks().stream().allMatch(i -> i != null && i >= 0 && i < input.manifest().hashes().size()), "Danh sách chunk không hợp lệ.");
        SharedFile file = context.state.files.get(input.manifest().id());
        if (file == null) {
            file = new SharedFile(); file.manifest = input.manifest(); file.status = "PENDING"; file.submittedBy = a.username;
            context.state.files.put(file.manifest.id(), file); context.store.log(a.username, "SUBMIT_FILE", file.manifest.name());
        } else access.require(file.manifest.sameContent(input.manifest()), "Manifest xung đột với file đã đăng ký.");
        file.holders.put(p.id, List.copyOf(input.chunks())); p.lastSeen = System.currentTimeMillis(); context.store.save();
        return access.fileView(file);
    }
    void unshare(String token, String peerId, String fileId) {
        access.ownPeer(access.account(token, true), peerId, false);
        SharedFile f = context.state.files.get(fileId);
        if (f != null) { f.holders.remove(peerId); context.store.save(); }
    }
    List<Map<String, Object>> files(String token, String query) {
        access.account(token, true); String q = query == null ? "" : query.toLowerCase(Locale.ROOT);
        return context.state.files.values().stream().filter(f -> "APPROVED".equals(f.status)
                && f.manifest.name().toLowerCase(Locale.ROOT).contains(q)).map(access::fileView).toList();
    }
    List<Map<String, Object>> sources(String token, String fileId) {
        access.account(token, true); SharedFile f = context.state.files.get(fileId);
        access.require(f != null && "APPROVED".equals(f.status), "File chưa được duyệt hoặc đã bị ẩn.");
        var result = new ArrayList<Map<String, Object>>();
        f.holders.forEach((id, chunks) -> {
            Peer p = context.state.peers.get(id);
            if (p != null && access.online(p)) {
                var view = new LinkedHashMap<>(access.peerView(p)); view.put("chunks", chunks); result.add(view);
            }
        }); return result;
    }
}
