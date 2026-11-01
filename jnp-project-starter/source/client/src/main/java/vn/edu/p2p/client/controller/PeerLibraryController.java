package vn.edu.p2p.client.controller;

import vn.edu.p2p.client.service.PeerLibraryService;
import vn.edu.p2p.client.dto.request.*;
import vn.edu.p2p.client.dto.response.*;
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
    private final PeerLibraryService service;

    public PeerLibraryController(PeerLibraryService service) {
        this.service = service;
    }

    @PostMapping("/library")
    public UploadResponse upload(@RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "false") boolean scanConsent, HttpServletRequest r)
            throws Exception {
        auth(r);
        return service.upload(file, scanConsent);
    }

    @PatchMapping("/library/{id}/sharing")
    public MessageResponse sharing(
            @PathVariable String id, @RequestBody Sharing b, HttpServletRequest r)
            throws Exception {
        auth(r);
        service.sharing(id, b.enabled());
        return new MessageResponse(b.enabled() ? "Đã bật chia sẻ." : "Đã dừng chia sẻ.");
    }

    @GetMapping("/library/{id}/content")
    public ResponseEntity<?> file(@PathVariable String id, HttpServletRequest r) {
        auth(r);
        var file = service.localFile(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(file.name(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .body(new FileSystemResource(file.path()));
    }

    @PatchMapping("/library/{id}/scan-consent")
    public MessageResponse scanConsent(@PathVariable String id, @RequestBody Sharing b, HttpServletRequest r) throws Exception {
        auth(r);
        service.scanConsent(id, b.enabled());
        return new MessageResponse(b.enabled() ? "Đã cho phép gửi file lên VirusTotal khi admin yêu cầu." : "Đã tắt quyền gửi file lên VirusTotal.");
    }
}
