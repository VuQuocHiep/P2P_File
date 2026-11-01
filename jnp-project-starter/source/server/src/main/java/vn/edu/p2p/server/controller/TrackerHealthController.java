package vn.edu.p2p.server.controller;

import vn.edu.p2p.server.service.TrackerHealthService;
import org.springframework.web.bind.annotation.*;
import vn.edu.p2p.server.dto.response.HealthResponse;
import java.util.*;
import vn.edu.p2p.server.dto.request.*;

import static vn.edu.p2p.server.controller.TrackerRequestAuth.token;

@RestController
@RequestMapping("/api")
public class TrackerHealthController {
    private final TrackerHealthService service;

    public TrackerHealthController(TrackerHealthService service) {
        this.service = service;
    }

    @GetMapping("/health")
    public HealthResponse health() {
        return service.health();
    }
}
