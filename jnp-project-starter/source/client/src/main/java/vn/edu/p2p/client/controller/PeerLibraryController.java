package vn.edu.p2p.client.controller;

import vn.edu.p2p.client.service.PeerEngine;
import vn.edu.p2p.client.dto.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static vn.edu.p2p.client.controller.PeerRequestAuth.auth;

@RestController
@RequestMapping("/api")
public class PeerLibraryController {
    private final PeerEngine engine;
    public PeerLibraryController(PeerEngine engine) { this.engine = engine; }
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
}
