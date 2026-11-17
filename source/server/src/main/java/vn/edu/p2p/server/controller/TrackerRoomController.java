package vn.edu.p2p.server.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.*;
import vn.edu.p2p.server.dto.request.*;
import vn.edu.p2p.server.dto.response.*;
import vn.edu.p2p.server.service.TrackerRoomService;

import static vn.edu.p2p.server.controller.TrackerRequestAuth.token;

@RestController
@RequestMapping("/api/rooms")
public class TrackerRoomController {
    private final TrackerRoomService service;

    public TrackerRoomController(TrackerRoomService service) {
        this.service = service;
    }

    @GetMapping
    public List<RoomResponse> rooms(HttpServletRequest r) {
        return service.rooms(token(r));
    }

    @PostMapping
    public RoomResponse create(@RequestBody RoomCreate body, HttpServletRequest r) {
        return service.create(token(r), body);
    }

    @PostMapping("/{id}/join")
    public RoomResponse join(
            @PathVariable String id, @RequestBody(required = false) RoomJoin body, HttpServletRequest r) {
        return service.join(token(r), id, body);
    }

    @PatchMapping("/{id}")
    public RoomResponse update(
            @PathVariable String id, @RequestBody RoomUpdate body, HttpServletRequest r) {
        return service.update(token(r), id, body);
    }

    @DeleteMapping("/{id}")
    public MessageResponse delete(@PathVariable String id, HttpServletRequest r) {
        service.delete(token(r), id);
        return new MessageResponse("Đã xoá phòng và các file của phòng.");
    }

    @DeleteMapping("/{id}/members/{username}")
    public MessageResponse removeMember(
            @PathVariable String id, @PathVariable String username, HttpServletRequest r) {
        service.removeMember(token(r), id, username);
        return new MessageResponse("Đã đuổi thành viên khỏi phòng.");
    }

    @GetMapping("/{id}/requests")
    public List<RoomMemberResponse> requests(@PathVariable String id, HttpServletRequest r) {
        return service.requests(token(r), id);
    }

    @PatchMapping("/{id}/requests/{username}")
    public RoomMemberResponse decide(
            @PathVariable String id,
            @PathVariable String username,
            @RequestBody RoomDecision body,
            HttpServletRequest r) {
        return service.decide(token(r), id, username, body);
    }

    @GetMapping("/{id}/members")
    public List<RoomMemberResponse> members(@PathVariable String id, HttpServletRequest r) {
        return service.members(token(r), id);
    }

    @GetMapping("/{id}/files")
    public List<FileResponse> files(@PathVariable String id, HttpServletRequest r) {
        return service.files(token(r), id);
    }
}
