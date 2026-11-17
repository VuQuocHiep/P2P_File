package vn.edu.p2p.client.controller;

import vn.edu.p2p.client.service.PeerHealthService;
import vn.edu.p2p.client.dto.request.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import vn.edu.p2p.client.dto.response.HealthResponse;
import java.util.*;

import static vn.edu.p2p.client.controller.PeerRequestAuth.auth;

@RestController
@RequestMapping("/api")
public class PeerHealthController {
    private final PeerHealthService service;

    public PeerHealthController(PeerHealthService service) {
        this.service = service;
    }

    @GetMapping("/health")
    public HealthResponse health() {
        return service.health();
    }
}
