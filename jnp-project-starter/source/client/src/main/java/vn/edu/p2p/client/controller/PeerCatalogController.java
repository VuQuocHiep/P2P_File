package vn.edu.p2p.client.controller;

import vn.edu.p2p.client.service.PeerCatalogService;
import vn.edu.p2p.client.dto.request.*;
import vn.edu.p2p.client.dto.response.*;
import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import static vn.edu.p2p.client.controller.PeerRequestAuth.auth;

@RestController
@RequestMapping("/api")
public class PeerCatalogController {
    private final PeerCatalogService service;

    public PeerCatalogController(PeerCatalogService service) {
        this.service = service;
    }

    @GetMapping("/catalog")
    public List<FileResponse> catalog(
            @RequestParam(defaultValue = "") String q, HttpServletRequest r) throws Exception {
        auth(r);
        return service.catalog(q);
    }

    @GetMapping("/files/{id}/sources")
    public List<SourceResponse> sources(@PathVariable String id, HttpServletRequest r)
            throws Exception {
        auth(r);
        return service.sources(id);
    }
}
