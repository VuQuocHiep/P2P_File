package vn.edu.p2p.client.controller;

import vn.edu.p2p.client.service.PeerEngine;
import vn.edu.p2p.client.dto.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import static vn.edu.p2p.client.controller.PeerRequestAuth.auth;

@RestController
@RequestMapping("/api")
public class PeerCatalogController {
    private final PeerEngine engine;
    public PeerCatalogController(PeerEngine engine) { this.engine = engine; }
    @GetMapping("/catalog") Object catalog(@RequestParam(defaultValue="") String q, HttpServletRequest r) throws Exception { auth(r); return engine.catalog(q); }
    @GetMapping("/files/{id}/sources") Object sources(@PathVariable String id, HttpServletRequest r) throws Exception { auth(r); return engine.sources(id); }
}
