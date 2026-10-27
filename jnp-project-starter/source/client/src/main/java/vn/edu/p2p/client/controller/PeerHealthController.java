package vn.edu.p2p.client.controller;

import vn.edu.p2p.client.service.PeerEngine;
import vn.edu.p2p.client.dto.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

import static vn.edu.p2p.client.controller.PeerRequestAuth.auth;

@RestController
@RequestMapping("/api")
public class PeerHealthController {
    private final PeerEngine engine;
    public PeerHealthController(PeerEngine engine) { this.engine = engine; }
    @GetMapping("/health") Object health() { return Map.of("status", "ok", "service", "PeerMesh Peer"); }
}
