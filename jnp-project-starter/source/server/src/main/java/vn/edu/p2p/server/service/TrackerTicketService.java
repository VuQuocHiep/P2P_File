package vn.edu.p2p.server.service;

import vn.edu.p2p.server.dto.request.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.p2p.server.model.*;
import vn.edu.p2p.server.entity.*;
import java.util.*;
import org.springframework.stereotype.Service;
import vn.edu.p2p.server.dto.response.*;

/** Registry operations serialized on the shared context monitor. */
@Service
public class TrackerTicketService {
    private final TrackerContext context;
    private final TrackerAccess access;

    public TrackerTicketService(TrackerContext context, TrackerAccess access) {
        this.context = context;
        this.access = access;
    }

    public TicketResponse ticket(String token, TicketRequest input) {
        synchronized (context) {
            var transaction = context.beginTransaction();
            try {
                AccountEntity a = access.account(token, true);
                FileEntity f = context.files.findFileById(input.fileId()).orElse(null);
                PeerEntity p = context.peers.findPeerById(input.sourceId()).orElse(null);
                access.require(
                        f != null
                                && "APPROVED".equals(f.status)
                                && p != null
                                && access.online(p)
                                && f.holders.containsKey(p.id),
                        "Nguồn không khả dụng.");
                context.tickets
                        .entrySet()
                        .removeIf(e -> e.getValue().expires() < System.currentTimeMillis());
                String ticket = UUID.randomUUID().toString();
                context.tickets.put(
                        ticket,
                        new Ticket(
                                a.username,
                                p.id,
                                f.manifest.id(),
                                System.currentTimeMillis() + 60_000));
                return new TicketResponse(ticket, 60);
            } catch (Exception | Error failure) {
                context.rollback(transaction);
                throw failure;
            } finally {
                context.commit(transaction);
            }
        }
    }

    public AuthorizationResponse authorize(String token, CheckTicket input) {
        synchronized (context) {
            var transaction = context.beginTransaction();
            try {
                PeerEntity p = access.ownPeer(access.account(token, true), input.peerId(), true);
                Ticket ticket = context.tickets.get(input.ticket());
                FileEntity f = context.files.findFileById(input.fileId()).orElse(null);
                if (ticket == null
                        || ticket.expires() < System.currentTimeMillis()
                        || !ticket.sourceId().equals(p.id)
                        || !ticket.fileId().equals(input.fileId())
                        || f == null
                        || !"APPROVED".equals(f.status)
                        || !f.holders.containsKey(p.id)
                        || !"APPROVED"
                                .equals(
                                        context.accounts
                                                .findByUsername(ticket.username())
                                                .orElse(null)
                                                .status))
                    throw new ResponseStatusException(
                            HttpStatus.FORBIDDEN, "Vé tải hết hạn hoặc quyền tải đã bị thu hồi.");
                return new AuthorizationResponse(true);
            } catch (Exception | Error failure) {
                context.rollback(transaction);
                throw failure;
            } finally {
                context.commit(transaction);
            }
        }
    }
}
