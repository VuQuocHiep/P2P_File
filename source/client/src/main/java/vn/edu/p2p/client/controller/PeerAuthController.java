package vn.edu.p2p.client.controller;

import vn.edu.p2p.client.service.PeerSessionService;
import vn.edu.p2p.client.service.PeerStatusService;
import vn.edu.p2p.client.dto.response.*;
import vn.edu.p2p.client.dto.request.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

import static vn.edu.p2p.client.controller.PeerRequestAuth.auth;

@RestController
@RequestMapping("/api")
public class PeerAuthController {
    private final PeerSessionService sessionService;
    private final PeerStatusService statusService;

    public PeerAuthController(PeerSessionService sessionService, PeerStatusService statusService) {
        this.sessionService = sessionService;
        this.statusService = statusService;
    }

    @PostMapping("/auth/register")
    public RegistrationResponse register(@RequestBody Registration body) throws Exception {
        return sessionService.register(body);
    }

    @PostMapping("/auth/login")
    public PeerStatusResponse login(@RequestBody Login body, HttpServletRequest request)
            throws Exception {
        PeerStatusResponse result = sessionService.login(body);
        var old = request.getSession(false);
        if (old != null) old.invalidate();
        request.getSession(true).setAttribute("authenticated", true);
        return result;
    }

    @PostMapping("/auth/logout")
    public MessageResponse logout(HttpServletRequest r) {
        auth(r);
        sessionService.logout();
        r.getSession().invalidate();
        return new MessageResponse("Đã đăng xuất.");
    }

    @GetMapping("/status")
    public PeerStatusView status(HttpServletRequest r) {
        var s = r.getSession(false);
        return s != null && Boolean.TRUE.equals(s.getAttribute("authenticated"))
                ? statusService.status()
                : new AnonymousStatusResponse(false);
    }
}
