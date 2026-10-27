package vn.edu.p2p.server.controller;

import vn.edu.p2p.server.service.TrackerService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import vn.edu.p2p.server.dto.*;

import static vn.edu.p2p.server.controller.TrackerRequestAuth.token;

@RestController
@RequestMapping("/api")
public class TrackerAuthController {
    private final TrackerService service;
    public TrackerAuthController(TrackerService service) { this.service = service; }
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
}
