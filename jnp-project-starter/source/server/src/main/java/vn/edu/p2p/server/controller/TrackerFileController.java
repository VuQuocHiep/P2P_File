package vn.edu.p2p.server.controller;

import vn.edu.p2p.server.service.TrackerService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import vn.edu.p2p.server.dto.*;

import static vn.edu.p2p.server.controller.TrackerRequestAuth.token;

@RestController
@RequestMapping("/api")
public class TrackerFileController {
    private final TrackerService service;
    public TrackerFileController(TrackerService service) { this.service = service; }
    @PostMapping("/files") Object share(@RequestBody Advertisement b, HttpServletRequest r) { return service.advertise(token(r), b); }
    @DeleteMapping("/files/{id}/holders/{peerId}") Object unshare(@PathVariable String id, @PathVariable String peerId, HttpServletRequest r) {
        service.unshare(token(r), peerId, id); return Map.of("message", "Đã dừng chia sẻ.");
    }
    @GetMapping("/files") Object files(@RequestParam(defaultValue="") String q, HttpServletRequest r) { return service.files(token(r), q); }
    @GetMapping("/files/{id}/sources") Object sources(@PathVariable String id, HttpServletRequest r) { return service.sources(token(r), id); }
}
