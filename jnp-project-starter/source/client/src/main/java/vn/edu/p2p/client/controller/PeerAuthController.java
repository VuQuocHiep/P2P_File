package vn.edu.p2p.client.controller;

import vn.edu.p2p.client.service.PeerEngine;
import vn.edu.p2p.client.dto.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

import static vn.edu.p2p.client.controller.PeerRequestAuth.auth;

@RestController
@RequestMapping("/api")
public class PeerAuthController {
    private final PeerEngine engine;
    public PeerAuthController(PeerEngine engine) { this.engine = engine; }
    @PostMapping("/auth/register") Object register(@RequestBody Registration body) throws Exception { return engine.register(body); }
    @PostMapping("/auth/login") Object login(@RequestBody Login body, HttpServletRequest request) throws Exception {
        if (body.username() == null || body.password() == null || body.name() == null || body.host() == null)
            throw new IllegalArgumentException("Điền đủ thông tin đăng nhập và peer.");
        Object result = engine.login(body.username(), body.password(), body.name(), body.host());
        var old = request.getSession(false); if (old != null) old.invalidate();
        request.getSession(true).setAttribute("authenticated", true); return result;
    }
    @PostMapping("/auth/logout") Object logout(HttpServletRequest r) {
        auth(r); engine.logout(); r.getSession().invalidate(); return Map.of("message", "Đã đăng xuất.");
    }
    @GetMapping("/status") Object status(HttpServletRequest r) {
        var s = r.getSession(false);
        return s != null && Boolean.TRUE.equals(s.getAttribute("authenticated")) ? engine.status() : Map.of("authenticated", false);
    }
}
