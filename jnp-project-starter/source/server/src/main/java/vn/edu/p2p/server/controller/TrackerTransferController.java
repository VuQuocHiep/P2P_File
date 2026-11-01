package vn.edu.p2p.server.controller;

import vn.edu.p2p.server.service.TrackerTicketService;
import vn.edu.p2p.server.service.TrackerTransferService;
import vn.edu.p2p.server.dto.response.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import vn.edu.p2p.server.dto.request.*;

import static vn.edu.p2p.server.controller.TrackerRequestAuth.token;

@RestController
@RequestMapping("/api")
public class TrackerTransferController {
    private final TrackerTicketService ticketService;
    private final TrackerTransferService transferService;

    public TrackerTransferController(
            TrackerTicketService ticketService, TrackerTransferService transferService) {
        this.ticketService = ticketService;
        this.transferService = transferService;
    }

    @PostMapping("/tickets")
    public TicketResponse ticket(@RequestBody TicketRequest b, HttpServletRequest r) {
        return ticketService.ticket(token(r), b);
    }

    @PostMapping("/tickets/authorize")
    public AuthorizationResponse authorize(@RequestBody CheckTicket b, HttpServletRequest r) {
        return ticketService.authorize(token(r), b);
    }

    @PostMapping("/transfers")
    public ProgressResponse progress(@RequestBody Progress b, HttpServletRequest r) {
        transferService.report(token(r), b);
        return new ProgressResponse(true);
    }
}
