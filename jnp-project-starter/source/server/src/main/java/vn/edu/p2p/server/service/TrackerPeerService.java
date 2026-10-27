package vn.edu.p2p.server.service;

import vn.edu.p2p.server.dto.*;
import vn.edu.p2p.server.model.*;
import java.net.InetAddress;
import java.util.*;

/** Registry operations invoked under the TrackerService monitor. */
final class TrackerPeerService {
    private final TrackerContext context;
    private final TrackerAccess access;
    TrackerPeerService(TrackerContext context, TrackerAccess access) {
        this.context = context;
        this.access = access;
    }
    Map<String, Object> registerPeer(String token, RegisterPeer input) throws Exception {
        Account a = access.account(token, true);
        access.require(input.name() != null && !input.name().isBlank() && input.name().length() <= 60, "Tên peer cần 1–60 ký tự.");
        access.require(input.host() != null && input.host().length() <= 45 && input.host().matches("[0-9a-fA-F:.]+"), "Host phải là địa chỉ IP.");
        var address = InetAddress.getByName(input.host());
        access.require(!address.isAnyLocalAddress() && !address.isMulticastAddress(), "Địa chỉ IP không hợp lệ.");
        access.require(input.port() >= 1024 && input.port() <= 65535, "TCP port cần trong khoảng 1024–65535.");
        String id = input.id();
        Peer p;
        if (id == null || id.isBlank()) {
            p = new Peer(); p.id = UUID.randomUUID().toString(); p.owner = a.username; p.status = "PENDING";
            context.state.peers.put(p.id, p);
        } else {
            p = access.ownPeer(a, id, false);
            if (!Objects.equals(p.host, input.host()) || p.port != input.port()) p.status = "PENDING";
        }
        p.name = input.name(); p.host = input.host(); p.port = input.port(); p.lastSeen = System.currentTimeMillis();
        context.store.log(a.username, "REGISTER_PEER", p.id); context.store.save(); return access.peerView(p);
    }
    Map<String, Object> heartbeat(String token, Heartbeat input) {
        Peer p = access.ownPeer(access.account(token, true), input.peerId(), false);
        p.lastSeen = System.currentTimeMillis(); context.store.save(); return access.peerView(p);
    }
}
