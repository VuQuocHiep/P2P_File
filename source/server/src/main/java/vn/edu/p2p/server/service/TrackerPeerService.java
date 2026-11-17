package vn.edu.p2p.server.service;

import vn.edu.p2p.server.dto.request.*;
import vn.edu.p2p.server.model.*;
import vn.edu.p2p.server.entity.*;
import java.net.InetAddress;
import java.util.*;
import org.springframework.stereotype.Service;
import vn.edu.p2p.server.dto.response.*;
import vn.edu.p2p.server.mapper.PeerMapper;

/** Registry operations serialized on the shared context monitor. */
@Service
public class TrackerPeerService {
    private final TrackerContext context;
    private final TrackerAccess access;
    private final PeerMapper peerMapper;

    public TrackerPeerService(TrackerContext context, TrackerAccess access, PeerMapper peerMapper) {
        this.context = context;
        this.access = access;
        this.peerMapper = peerMapper;
    }

    public PeerResponse registerPeer(String token, RegisterPeer input) throws Exception {
        synchronized (context) {
            var transaction = context.beginTransaction();
            try {
                AccountEntity a = access.account(token, true);
                access.require(
                        input.name() != null
                                && !input.name().isBlank()
                                && input.name().length() <= 60,
                        "Tên peer cần 1–60 ký tự.");
                access.require(
                        input.host() != null
                                && input.host().length() <= 45
                                && input.host().matches("[0-9a-fA-F:.]+"),
                        "Host phải là địa chỉ IP.");
                var address = InetAddress.getByName(input.host());
                access.require(
                        !address.isAnyLocalAddress() && !address.isMulticastAddress(),
                        "Địa chỉ IP không hợp lệ.");
                access.require(
                        input.port() >= 1024 && input.port() <= 65535,
                        "TCP port cần trong khoảng 1024–65535.");
                String id = input.id();
                PeerEntity p;
                if (id == null || id.isBlank()) {
                    p = new PeerEntity();
                    p.id = UUID.randomUUID().toString();
                    p.owner = a.username;
                    p.status = "PENDING";
                    p.position = context.nextPeerPosition++;
                } else {
                    p = access.ownPeer(a, id, false);
                    if (!Objects.equals(p.host, input.host()) || p.port != input.port())
                        p.status = "PENDING";
                }
                p.name = input.name();
                p.host = input.host();
                p.port = input.port();
                p.lastSeen = System.currentTimeMillis();
                p = context.peers.save(p);
                context.log(a.username, "REGISTER_PEER", p.id);
                return peerMapper.toResponse(p, access.online(p));
            } catch (Exception | Error failure) {
                context.rollback(transaction);
                throw failure;
            } finally {
                context.commit(transaction);
            }
        }
    }

    public PeerResponse heartbeat(String token, Heartbeat input) {
        synchronized (context) {
            var transaction = context.beginTransaction();
            try {
                PeerEntity p = access.ownPeer(access.account(token, true), input.peerId(), false);
                p.lastSeen = System.currentTimeMillis();
                return peerMapper.toResponse(p, access.online(p));
            } catch (Exception | Error failure) {
                context.rollback(transaction);
                throw failure;
            } finally {
                context.commit(transaction);
            }
        }
    }
}
