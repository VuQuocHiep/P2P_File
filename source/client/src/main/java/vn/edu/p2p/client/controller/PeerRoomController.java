package vn.edu.p2p.client.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.*;
import vn.edu.p2p.client.dto.request.*;
import vn.edu.p2p.client.dto.response.*;
import vn.edu.p2p.client.service.PeerRoomService;

import static vn.edu.p2p.client.controller.PeerRequestAuth.auth;

@RestController
@RequestMapping("/api/rooms")
public class PeerRoomController {
    private final PeerRoomService service;

    public PeerRoomController(PeerRoomService service) {
        this.service = service;
    }

    @GetMapping
    public List<RoomView> rooms(HttpServletRequest r) throws Exception {
        auth(r);
        return service.rooms();
    }

    @PostMapping
    public RoomView create(@RequestBody RoomCreate body, HttpServletRequest r) throws Exception {
        auth(r);
        return service.create(body);
    }

    @PostMapping("/{id}/join")
    public RoomView join(
            @PathVariable String id, @RequestBody(required = false) RoomJoin body, HttpServletRequest r)
            throws Exception {
        auth(r);
        return service.join(id, body);
    }

    @PatchMapping("/{id}")
    public RoomView update(
            @PathVariable String id, @RequestBody RoomUpdate body, HttpServletRequest r)
            throws Exception {
        auth(r);
        return service.update(id, body);
    }

    @DeleteMapping("/{id}")
    public MessageResponse delete(@PathVariable String id, HttpServletRequest r) throws Exception {
        auth(r);
        return service.delete(id);
    }

    @DeleteMapping("/{id}/members/{username}")
    public MessageResponse removeMember(
            @PathVariable String id, @PathVariable String username, HttpServletRequest r)
            throws Exception {
        auth(r);
        return service.removeMember(id, username);
    }

    @GetMapping("/{id}/requests")
    public List<RoomMemberView> requests(@PathVariable String id, HttpServletRequest r)
            throws Exception {
        auth(r);
        return service.requests(id);
    }

    @PatchMapping("/{id}/requests/{username}")
    public RoomMemberView decide(
            @PathVariable String id,
            @PathVariable String username,
            @RequestBody RoomDecision body,
            HttpServletRequest r)
            throws Exception {
        auth(r);
        return service.decide(id, username, body);
    }

    @GetMapping("/{id}/members")
    public List<RoomMemberView> members(@PathVariable String id, HttpServletRequest r)
            throws Exception {
        auth(r);
        return service.members(id);
    }

    @GetMapping("/{id}/files")
    public List<FileResponse> files(@PathVariable String id, HttpServletRequest r) throws Exception {
        auth(r);
        return service.files(id);
    }

    @PatchMapping("/{id}/files/{fileId}")
    public MessageResponse share(
            @PathVariable String id, @PathVariable String fileId, HttpServletRequest r)
            throws Exception {
        auth(r);
        return service.share(id, fileId);
    }
}
