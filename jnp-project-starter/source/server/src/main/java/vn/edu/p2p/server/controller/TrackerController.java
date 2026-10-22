package vn.edu.p2p.server.controller;

import vn.edu.p2p.server.service.TrackerService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import vn.edu.p2p.server.dto.TrackerRequests.*;

@RestController
@RequestMapping("/api")
public class TrackerController {
    private final TrackerService service;
    public TrackerController(TrackerService service) { this.service = service; }
    private String token(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) return header.substring(7);
        var session = request.getSession(false);
        return session == null ? "" : Objects.toString(session.getAttribute("token"), "");
    }
    @GetMapping("/health") Object health() { return Map.of("status", "ok", "service", "PeerMesh Tracker"); }
    @PostMapping("/auth/register") Object register(@RequestBody Login body) { return service.register(body); }
    @PostMapping("/auth/login") Object login(@RequestBody Login body, HttpServletRequest request) {
        var result = service.login(body);
        var old = request.getSession(false); if (old != null) old.invalidate();
        request.getSession(true).setAttribute("token", result.get("token")); return result;
    }
    @PostMapping("/auth/logout") Object logout(HttpServletRequest request) {
        service.logout(token(request)); var session = request.getSession(false); if (session != null) session.invalidate();
        return Map.of("message", "Đã đăng xuất.");
    }
    @GetMapping("/auth/me") Object me(HttpServletRequest r) { return service.me(token(r)); }
    @PostMapping("/peers") Object peer(@RequestBody RegisterPeer b, HttpServletRequest r) throws Exception { return service.registerPeer(token(r), b); }
    @PostMapping("/peers/heartbeat") Object heartbeat(@RequestBody Heartbeat b, HttpServletRequest r) { return service.heartbeat(token(r), b); }
    @PostMapping("/files") Object share(@RequestBody Advertisement b, HttpServletRequest r) { return service.advertise(token(r), b); }
    @DeleteMapping("/files/{id}/holders/{peerId}") Object unshare(@PathVariable String id, @PathVariable String peerId, HttpServletRequest r) {
        service.unshare(token(r), peerId, id); return Map.of("message", "Đã dừng chia sẻ.");
    }
    @GetMapping("/files") Object files(@RequestParam(defaultValue="") String q, HttpServletRequest r) { return service.files(token(r), q); }
    @GetMapping("/files/{id}/sources") Object sources(@PathVariable String id, HttpServletRequest r) { return service.sources(token(r), id); }
    @PostMapping("/tickets") Object ticket(@RequestBody TicketRequest b, HttpServletRequest r) { return service.ticket(token(r), b); }
    @PostMapping("/tickets/authorize") Object authorize(@RequestBody CheckTicket b, HttpServletRequest r) { return service.authorize(token(r), b); }
    @PostMapping("/transfers") Object progress(@RequestBody Progress b, HttpServletRequest r) { service.report(token(r), b); return Map.of("ok", true); }
    @GetMapping("/admin/dashboard") Object dashboard(HttpServletRequest r) { return service.dashboard(token(r)); }
    @PatchMapping("/admin/{kind}/{id}") Object moderate(@PathVariable String kind, @PathVariable String id,
            @RequestBody Moderation b, HttpServletRequest r) {
        service.moderate(token(r), kind, id, b.status()); return Map.of("message", "Đã cập nhật trạng thái.");
    }
}
