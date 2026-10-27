package vn.edu.p2p.server.service;

import vn.edu.p2p.server.dto.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.p2p.server.model.*;
import java.util.*;

/** Registry operations invoked under the TrackerService monitor. */
final class TrackerTicketService {
    private final TrackerContext context;
    private final TrackerAccess access;
    TrackerTicketService(TrackerContext context, TrackerAccess access) {
        this.context = context;
        this.access = access;
    }
    Map<String, Object> ticket(String token, TicketRequest input) {
        Account a = access.account(token, true); SharedFile f = context.state.files.get(input.fileId()); Peer p = context.state.peers.get(input.sourceId());
        access.require(f != null && "APPROVED".equals(f.status) && p != null && access.online(p) && f.holders.containsKey(p.id), "Nguồn không khả dụng.");
        context.tickets.entrySet().removeIf(e -> e.getValue().expires() < System.currentTimeMillis());
        String ticket = UUID.randomUUID().toString();
        context.tickets.put(ticket, new Ticket(a.username, p.id, f.manifest.id(), System.currentTimeMillis() + 60_000));
        return Map.of("ticket", ticket, "expiresIn", 60);
    }
    Map<String, Object> authorize(String token, CheckTicket input) {
        Peer p = access.ownPeer(access.account(token, true), input.peerId(), true); Ticket ticket = context.tickets.get(input.ticket());
        SharedFile f = context.state.files.get(input.fileId());
        if (ticket == null || ticket.expires() < System.currentTimeMillis() || !ticket.sourceId().equals(p.id)
                || !ticket.fileId().equals(input.fileId()) || f == null || !"APPROVED".equals(f.status)
                || !f.holders.containsKey(p.id) || !"APPROVED".equals(context.state.accounts.get(ticket.username()).status))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Vé tải hết hạn hoặc quyền tải đã bị thu hồi.");
        return Map.of("allowed", true);
    }
}
