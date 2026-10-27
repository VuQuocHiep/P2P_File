package vn.edu.p2p.server.controller;

import vn.edu.p2p.server.service.TrackerService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import vn.edu.p2p.server.dto.*;

import static vn.edu.p2p.server.controller.TrackerRequestAuth.token;

@RestController
@RequestMapping("/api")
public class TrackerTransferController {
    private final TrackerService service;
    public TrackerTransferController(TrackerService service) { this.service = service; }
    @PostMapping("/tickets") Object ticket(@RequestBody TicketRequest b, HttpServletRequest r) { return service.ticket(token(r), b); }
    @PostMapping("/tickets/authorize") Object authorize(@RequestBody CheckTicket b, HttpServletRequest r) { return service.authorize(token(r), b); }
    @PostMapping("/transfers") Object progress(@RequestBody Progress b, HttpServletRequest r) { service.report(token(r), b); return Map.of("ok", true); }
}
