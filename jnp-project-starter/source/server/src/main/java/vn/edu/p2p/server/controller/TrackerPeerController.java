package vn.edu.p2p.server.controller;

import vn.edu.p2p.server.service.TrackerPeerService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import vn.edu.p2p.server.dto.response.*;
import vn.edu.p2p.server.dto.request.*;

import static vn.edu.p2p.server.controller.TrackerRequestAuth.token;

@RestController
@RequestMapping("/api")
public class TrackerPeerController {
    private final TrackerPeerService service;

    public TrackerPeerController(TrackerPeerService service) {
        this.service = service;
    }

    @PostMapping("/peers")
    public PeerResponse peer(@RequestBody RegisterPeer b, HttpServletRequest r) throws Exception {
        return service.registerPeer(token(r), b);
    }

    @PostMapping("/peers/heartbeat")
    public PeerResponse heartbeat(@RequestBody Heartbeat b, HttpServletRequest r) {
        return service.heartbeat(token(r), b);
    }
}
