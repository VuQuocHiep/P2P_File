package vn.edu.p2p.client.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import vn.edu.p2p.client.dto.request.Bandwidth;
import vn.edu.p2p.client.dto.response.BandwidthResponse;
import vn.edu.p2p.client.service.PeerBandwidthService;

import static vn.edu.p2p.client.controller.PeerRequestAuth.auth;

@RestController
@RequestMapping("/api/settings/bandwidth")
public class PeerBandwidthController {
    private final PeerBandwidthService service;

    public PeerBandwidthController(PeerBandwidthService service) {
        this.service = service;
    }

    @GetMapping
    public BandwidthResponse settings(HttpServletRequest request) {
        auth(request);
        return service.settings();
    }

    @PatchMapping
    public BandwidthResponse update(@RequestBody Bandwidth body, HttpServletRequest request) {
        auth(request);
        return service.update(body);
    }
}
