package vn.edu.p2p.server.controller;

import vn.edu.p2p.server.service.TrackerService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import vn.edu.p2p.server.dto.*;

import static vn.edu.p2p.server.controller.TrackerRequestAuth.token;

@RestController
@RequestMapping("/api")
public class TrackerAdminController {
    private final TrackerService service;
    public TrackerAdminController(TrackerService service) { this.service = service; }
    @GetMapping("/admin/dashboard") Object dashboard(HttpServletRequest r) { return service.dashboard(token(r)); }
    @PatchMapping("/admin/{kind}/{id}") Object moderate(@PathVariable String kind, @PathVariable String id,
            @RequestBody Moderation b, HttpServletRequest r) {
        service.moderate(token(r), kind, id, b.status()); return Map.of("message", "Đã cập nhật trạng thái.");
    }
}
