package vn.edu.p2p.client.controller;

import vn.edu.p2p.client.service.PeerDownloadService;
import vn.edu.p2p.client.dto.request.*;
import vn.edu.p2p.client.dto.response.*;
import java.util.Map;
import vn.edu.p2p.client.model.ChunkSource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

import static vn.edu.p2p.client.controller.PeerRequestAuth.auth;

@RestController
@RequestMapping("/api")
public class PeerDownloadController {
    private final PeerDownloadService service;

    public PeerDownloadController(PeerDownloadService service) {
        this.service = service;
    }

    @PostMapping("/downloads")
    public DownloadResponse download(@RequestBody Download body, HttpServletRequest r)
            throws Exception {
        auth(r);
        return service.download(body.fileId());
    }

    @GetMapping("/downloads/{id}/chunks")
    public Map<Integer, ChunkSource> chunkHistory(@PathVariable String id, HttpServletRequest r) {
        auth(r);
        return service.chunkHistory(id);
    }

    @PostMapping("/downloads/{id}/pause")
    public MessageResponse pause(@PathVariable String id, HttpServletRequest r) {
        auth(r);
        service.pause(id);
        return new MessageResponse("Đã yêu cầu tạm dừng.");
    }

    @PostMapping("/downloads/{id}/resume")
    public MessageResponse resume(@PathVariable String id, HttpServletRequest r) {
        auth(r);
        service.resume(id);
        return new MessageResponse("Đã tiếp tục tải.");
    }
}
