package vn.edu.p2p.server.controller;

import vn.edu.p2p.server.service.TrackerFileService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import vn.edu.p2p.server.dto.response.*;
import java.util.List;
import java.util.*;
import vn.edu.p2p.server.dto.request.*;

import static vn.edu.p2p.server.controller.TrackerRequestAuth.token;

@RestController
@RequestMapping("/api")
public class TrackerFileController {
    private final TrackerFileService service;

    public TrackerFileController(TrackerFileService service) {
        this.service = service;
    }

    @PostMapping("/files")
    public FileResponse share(@RequestBody Advertisement b, HttpServletRequest r) {
        return service.advertise(token(r), b);
    }

    @GetMapping("/files/deletions")
    public List<String> deletions(HttpServletRequest r) {
        return service.deletedFileIds(token(r));
    }

    @DeleteMapping("/files/{id}/holders/{peerId}")
    public MessageResponse unshare(
            @PathVariable String id, @PathVariable String peerId, HttpServletRequest r) {
        service.unshare(token(r), peerId, id);
        return new MessageResponse("Đã dừng chia sẻ.");
    }

    @GetMapping("/files")
    public List<FileResponse> files(
            @RequestParam(defaultValue = "") String q, HttpServletRequest r) {
        return service.files(token(r), q);
    }

    @GetMapping("/files/{id}/sources")
    public List<SourceResponse> sources(@PathVariable String id, HttpServletRequest r) {
        return service.sources(token(r), id);
    }
}
