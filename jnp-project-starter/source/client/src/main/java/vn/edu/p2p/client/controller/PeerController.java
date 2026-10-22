package vn.edu.p2p.client.controller;

import vn.edu.p2p.client.service.PeerEngine;
import vn.edu.p2p.client.dto.PeerRequests.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@RequestMapping("/api")
public class PeerController {
    private final PeerEngine engine;
    public PeerController(PeerEngine engine) { this.engine = engine; }
    private void auth(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session == null || !Boolean.TRUE.equals(session.getAttribute("authenticated")))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Hãy đăng nhập vào peer này.");
    }
    @GetMapping("/health") Object health() { return Map.of("status", "ok", "service", "PeerMesh Peer"); }
    @PostMapping("/auth/register") Object register(@RequestBody Registration body) throws Exception { return engine.register(body); }
    @PostMapping("/auth/login") Object login(@RequestBody Login body, HttpServletRequest request) throws Exception {
        if (body.username() == null || body.password() == null || body.name() == null || body.host() == null)
            throw new IllegalArgumentException("Điền đủ thông tin đăng nhập và peer.");
        Object result = engine.login(body.username(), body.password(), body.name(), body.host());
        var old = request.getSession(false); if (old != null) old.invalidate();
        request.getSession(true).setAttribute("authenticated", true); return result;
    }
    @PostMapping("/auth/logout") Object logout(HttpServletRequest r) {
        auth(r); engine.logout(); r.getSession().invalidate(); return Map.of("message", "Đã đăng xuất.");
    }
    @GetMapping("/status") Object status(HttpServletRequest r) {
        var s = r.getSession(false);
        return s != null && Boolean.TRUE.equals(s.getAttribute("authenticated")) ? engine.status() : Map.of("authenticated", false);
    }
    @GetMapping("/catalog") Object catalog(@RequestParam(defaultValue="") String q, HttpServletRequest r) throws Exception { auth(r); return engine.catalog(q); }
    @GetMapping("/files/{id}/sources") Object sources(@PathVariable String id, HttpServletRequest r) throws Exception { auth(r); return engine.sources(id); }
    @PostMapping("/library") Object upload(@RequestParam("file") MultipartFile file, HttpServletRequest r) throws Exception { auth(r); return engine.upload(file); }
    @PatchMapping("/library/{id}/sharing") Object sharing(@PathVariable String id, @RequestBody Sharing b, HttpServletRequest r) throws Exception {
        auth(r); engine.sharing(id, b.enabled()); return Map.of("message", b.enabled() ? "Đã bật chia sẻ." : "Đã dừng chia sẻ.");
    }
    @GetMapping("/library/{id}/content") ResponseEntity<?> file(@PathVariable String id, HttpServletRequest r) {
        auth(r); var file = engine.localFile(id);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.name(), StandardCharsets.UTF_8).build().toString())
                .body(new FileSystemResource(file.path()));
    }
    @PostMapping("/downloads") Object download(@RequestBody Download body, HttpServletRequest r) throws Exception { auth(r); return engine.download(body.fileId()); }
    @PostMapping("/lab/fault") Object fault(@RequestBody Fault body, HttpServletRequest r) {
        auth(r); engine.simulateFault(body.mode(), body.delayMs(), body.seconds());
        return Map.of("message", "Đã cập nhật mô phỏng lỗi nguồn.");
    }
    @PostMapping("/lab/benchmark") Object benchmark(@RequestBody Download body, HttpServletRequest r) throws Exception {
        auth(r); return engine.startBenchmark(body.fileId());
    }
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
