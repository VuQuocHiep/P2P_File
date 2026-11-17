package vn.edu.p2p.server.controller;

import vn.edu.p2p.server.service.TrackerAuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import vn.edu.p2p.server.dto.response.*;
import java.util.*;
import vn.edu.p2p.server.dto.request.*;

import static vn.edu.p2p.server.controller.TrackerRequestAuth.token;

@RestController
@RequestMapping("/api")
public class TrackerAuthController {
    private final TrackerAuthService service;

    public TrackerAuthController(TrackerAuthService service) {
        this.service = service;
    }

    @PostMapping("/auth/register")
    public RegistrationResponse register(@RequestBody Login body) {
        return service.register(body);
    }

    @PostMapping("/auth/login")
    public LoginResponse login(@RequestBody Login body, HttpServletRequest request) {
        var result = service.login(body);
        var old = request.getSession(false);
        if (old != null) old.invalidate();
        request.getSession(true).setAttribute("token", result.token());
        return result;
    }

    @PostMapping("/auth/logout")
    public MessageResponse logout(HttpServletRequest request) {
        service.logout(token(request));
        var session = request.getSession(false);
        if (session != null) session.invalidate();
        return new MessageResponse("Đã đăng xuất.");
    }

    @GetMapping("/auth/me")
    public UserResponse me(HttpServletRequest r) {
        return service.me(token(r));
    }
}
