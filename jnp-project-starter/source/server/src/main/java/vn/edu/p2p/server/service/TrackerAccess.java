package vn.edu.p2p.server.service;

import vn.edu.p2p.server.dto.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.p2p.server.model.*;
import java.util.*;

/** Registry operations invoked under the TrackerService monitor. */
final class TrackerAccess {
    private final TrackerContext context;
    TrackerAccess(TrackerContext context) {
        this.context = context;
    }
    static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
    static void checkName(String value) {
        require(value != null && value.matches("[a-zA-Z0-9_.-]{3,40}"), "Tên tài khoản cần 3–40 ký tự chữ, số, _, . hoặc -.");
    }
    Account account(String token, boolean approved) {
        Session session = context.sessions.get(token);
        if (session == null || session.expires() < System.currentTimeMillis())
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Cần đăng nhập lại.");
        Account a = context.state.accounts.get(session.username());
        if (a == null || "BLOCKED".equals(a.status) || (approved && !"APPROVED".equals(a.status)))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tài khoản chưa được duyệt hoặc đã bị khóa.");
        return a;
    }
    Account admin(String token) {
        Account a = account(token, true);
        if (!"ADMIN".equals(a.role)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chỉ dành cho quản trị viên.");
        return a;
    }
    Peer ownPeer(Account a, String id, boolean approved) {
        Peer p = context.state.peers.get(id);
        if (p == null || !p.owner.equals(a.username) || (approved && !"APPROVED".equals(p.status)))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Peer không thuộc tài khoản hoặc chưa được duyệt.");
        return p;
    }
    Map<String, Object> userView(Account a) {
        return Map.of("username", a.username, "role", a.role, "status", a.status, "created", a.created);
    }
    boolean online(Peer p) {
        Account owner = context.state.accounts.get(p.owner);
        return "APPROVED".equals(p.status) && owner != null && "APPROVED".equals(owner.status)
                && System.currentTimeMillis() - p.lastSeen < 20_000;
    }
    Map<String, Object> peerView(Peer p) {
        return Map.of("id", p.id, "owner", p.owner, "name", p.name, "host", p.host,
                "port", p.port, "status", p.status, "online", online(p), "lastSeen", p.lastSeen);
    }
    Map<String, Object> fileView(SharedFile f) {
        long sources = f.holders.keySet().stream().map(context.state.peers::get).filter(Objects::nonNull).filter(this::online).count();
        return Map.of("manifest", f.manifest, "status", f.status, "submittedBy", f.submittedBy,
                "sources", sources, "holders", f.holders.size());
    }
}
