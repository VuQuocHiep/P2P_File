package vn.edu.p2p.server.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.p2p.server.model.*;
import vn.edu.p2p.server.entity.*;
import java.util.*;
import org.springframework.stereotype.Component;

/** Access checks and availability rules, called under the shared context monitor. */
@Component
final class TrackerAccess {
    private final TrackerContext context;

    TrackerAccess(TrackerContext context) {
        this.context = context;
    }

    static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }

    static void checkName(String value) {
        require(
                value != null && value.matches("[a-zA-Z0-9_.-]{3,40}"),
                "Tên tài khoản cần 3–40 ký tự chữ, số, _, . hoặc -.");
    }

    AccountEntity account(String token, boolean approved) {
        Session session = context.sessions.get(token);
        if (session == null || session.expires() < System.currentTimeMillis())
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Cần đăng nhập lại.");
        AccountEntity a = context.accounts.findByUsername(session.username()).orElse(null);
        if (a == null || "BLOCKED".equals(a.status) || (approved && !"APPROVED".equals(a.status)))
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Tài khoản chưa được duyệt hoặc đã bị khóa.");
        return a;
    }

    AccountEntity admin(String token) {
        AccountEntity a = account(token, true);
        if (!"ADMIN".equals(a.role))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chỉ dành cho quản trị viên.");
        return a;
    }

    PeerEntity ownPeer(AccountEntity a, String id, boolean approved) {
        PeerEntity p = context.peers.findPeerById(id).orElse(null);
        if (p == null || !p.owner.equals(a.username) || (approved && !"APPROVED".equals(p.status)))
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Peer không thuộc tài khoản hoặc chưa được duyệt.");
        return p;
    }

    boolean online(PeerEntity p) {
        AccountEntity owner = context.accounts.findByUsername(p.owner).orElse(null);
        return "APPROVED".equals(p.status)
                && owner != null
                && "APPROVED".equals(owner.status)
                && System.currentTimeMillis() - p.lastSeen < 20_000;
    }

    long onlineSources(FileEntity file) {
        return file.holders.keySet().stream()
                .map(id -> context.peers.findPeerById(id).orElse(null))
                .filter(Objects::nonNull)
                .filter(this::online)
                .count();
    }
}
