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
public class PeerDownloadController {
    private final PeerEngine engine;
    public PeerDownloadController(PeerEngine engine) { this.engine = engine; }
    @PostMapping("/downloads") Object download(@RequestBody Download body, HttpServletRequest r) throws Exception { auth(r); return engine.download(body.fileId()); }
    @GetMapping("/downloads/{id}/chunks") Object chunkHistory(@PathVariable String id, HttpServletRequest r) {
        auth(r); return engine.chunkHistory(id);
    }
    @PostMapping("/downloads/{id}/pause") Object pause(@PathVariable String id, HttpServletRequest r) {
        auth(r); engine.pause(id); return Map.of("message", "Đã yêu cầu tạm dừng.");
    }
    @PostMapping("/downloads/{id}/resume") Object resume(@PathVariable String id, HttpServletRequest r) {
        auth(r); engine.resume(id); return Map.of("message", "Đã tiếp tục tải.");
    }
}
