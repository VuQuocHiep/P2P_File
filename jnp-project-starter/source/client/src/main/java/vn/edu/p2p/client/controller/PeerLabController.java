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
public class PeerLabController {
    private final PeerEngine engine;
    public PeerLabController(PeerEngine engine) { this.engine = engine; }
    @PostMapping("/lab/fault") Object fault(@RequestBody Fault body, HttpServletRequest r) {
        auth(r); engine.simulateFault(body.mode(), body.delayMs(), body.seconds());
        return Map.of("message", "Đã cập nhật mô phỏng lỗi nguồn.");
    }
    @PostMapping("/lab/benchmark") Object benchmark(@RequestBody Download body, HttpServletRequest r) throws Exception {
        auth(r); return engine.startBenchmark(body.fileId());
    }
}
