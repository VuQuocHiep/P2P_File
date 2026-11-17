package vn.edu.p2p.client.controller;

import vn.edu.p2p.client.service.PeerLabService;
import vn.edu.p2p.client.dto.request.*;
import vn.edu.p2p.client.dto.response.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

import static vn.edu.p2p.client.controller.PeerRequestAuth.auth;

@RestController
@RequestMapping("/api")
public class PeerLabController {
    private final PeerLabService service;

    public PeerLabController(PeerLabService service) {
        this.service = service;
    }

    @PostMapping("/lab/fault")
    public MessageResponse fault(@RequestBody Fault body, HttpServletRequest r) {
        auth(r);
        service.simulateFault(body.mode(), body.delayMs(), body.seconds());
        return new MessageResponse("Đã cập nhật mô phỏng lỗi nguồn.");
    }

    @PostMapping("/lab/benchmark")
    public MessageResponse benchmark(@RequestBody Download body, HttpServletRequest r)
            throws Exception {
        auth(r);
        return service.startBenchmark(body.fileId());
    }
}
