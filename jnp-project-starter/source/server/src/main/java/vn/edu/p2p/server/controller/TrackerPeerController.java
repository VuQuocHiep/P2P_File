package vn.edu.p2p.server.controller;

import vn.edu.p2p.server.service.TrackerService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import vn.edu.p2p.server.dto.*;

import static vn.edu.p2p.server.controller.TrackerRequestAuth.token;

@RestController
@RequestMapping("/api")
public class TrackerPeerController {
    private final TrackerService service;
    public TrackerPeerController(TrackerService service) { this.service = service; }
    @PostMapping("/peers") Object peer(@RequestBody RegisterPeer b, HttpServletRequest r) throws Exception { return service.registerPeer(token(r), b); }
    @PostMapping("/peers/heartbeat") Object heartbeat(@RequestBody Heartbeat b, HttpServletRequest r) { return service.heartbeat(token(r), b); }
}
