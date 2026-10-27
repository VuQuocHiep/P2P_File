package vn.edu.p2p.server.service;

import vn.edu.p2p.server.dto.*;
import vn.edu.p2p.server.model.*;
import java.util.*;

/** Registry operations invoked under the TrackerService monitor. */
final class TrackerAdminService {
    private final TrackerContext context;
    private final TrackerAccess access;
    TrackerAdminService(TrackerContext context, TrackerAccess access) {
        this.context = context;
        this.access = access;
    }
    Map<String, Object> dashboard(String token) {
        access.admin(token);
        return Map.of("accounts", context.state.accounts.values().stream().map(access::userView).toList(),
                "peers", context.state.peers.values().stream().map(access::peerView).toList(),
                "files", context.state.files.values().stream().map(access::fileView).toList(),
                "transfers", new ArrayList<>(context.state.transfers.values()),
                "audit", context.state.audit.stream().sorted(Comparator.comparingLong(Audit::time).reversed()).limit(100).toList());
    }
    void moderate(String token, String kind, String id, String status) {
        Account a = access.admin(token);
        access.require(Set.of("APPROVED", "PENDING", "BLOCKED").contains(status), "Trạng thái không hợp lệ.");
        switch (kind) {
            case "accounts" -> {
                Account target = context.state.accounts.get(id); access.require(target != null, "Không có tài khoản.");
                access.require(!"ADMIN".equals(target.role), "Không thay đổi trạng thái quản trị viên."); target.status = status;
            }
            case "peers" -> { Peer p = context.state.peers.get(id); access.require(p != null, "Không có peer."); p.status = status; }
            case "files" -> { SharedFile f = context.state.files.get(id); access.require(f != null, "Không có file."); f.status = status; }
            default -> throw new IllegalArgumentException("Loại đối tượng không hợp lệ.");
        }
        context.store.log(a.username, kind + "_" + status, id); context.store.save();
    }
}
