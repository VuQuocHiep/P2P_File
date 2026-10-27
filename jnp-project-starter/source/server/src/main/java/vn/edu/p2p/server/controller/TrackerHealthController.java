package vn.edu.p2p.server.controller;

import vn.edu.p2p.server.service.TrackerService;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import vn.edu.p2p.server.dto.*;

import static vn.edu.p2p.server.controller.TrackerRequestAuth.token;

@RestController
@RequestMapping("/api")
public class TrackerHealthController {
    private final TrackerService service;
    public TrackerHealthController(TrackerService service) { this.service = service; }
    @GetMapping("/health") Object health() { return Map.of("status", "ok", "service", "PeerMesh Tracker"); }
}
