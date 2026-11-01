package vn.edu.p2p.server.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.p2p.server.service.TrackerScanJobService;
import vn.edu.p2p.server.dto.response.*;
import static vn.edu.p2p.server.controller.TrackerRequestAuth.token;

@RestController
@RequestMapping("/api")
public class TrackerScanJobController {
    private final TrackerScanJobService service;
    public TrackerScanJobController(TrackerScanJobService service) { this.service = service; }

    @PostMapping("/admin/files/{id}/scan-request")
    public MalwareReport request(@PathVariable String id, HttpServletRequest r) { return service.request(token(r), id); }

    @DeleteMapping("/admin/files/{id}/scan-request")
    public MessageResponse cancel(@PathVariable String id, HttpServletRequest r) {
        service.cancel(token(r), id); return new MessageResponse("Đã hủy yêu cầu quét trên server.");
    }

    @PostMapping("/peers/{peerId}/scan-jobs/claim")
    public Object claim(@PathVariable String peerId, HttpServletRequest r) {
        var job = service.claim(token(r), peerId);
        return job == null ? java.util.Map.of() : job;
    }

    @PostMapping(value = "/peers/{peerId}/scan-jobs/{id}/content", consumes = "multipart/form-data")
    public MalwareReport upload(@PathVariable String peerId, @PathVariable String id,
            @RequestHeader("X-Scan-Lease") String lease, @RequestParam MultipartFile file, HttpServletRequest r) throws Exception {
        return service.upload(token(r), peerId, id, lease, file);
    }

    @PostMapping("/peers/{peerId}/scan-jobs/{id}/release")
    public MessageResponse release(@PathVariable String peerId, @PathVariable String id,
            @RequestHeader("X-Scan-Lease") String lease, HttpServletRequest r) {
        service.release(token(r), peerId, id, lease); return new MessageResponse("Đã trả yêu cầu về hàng chờ.");
    }
}
