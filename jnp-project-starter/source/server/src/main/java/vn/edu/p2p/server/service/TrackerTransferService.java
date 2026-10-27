package vn.edu.p2p.server.service;

import vn.edu.p2p.server.dto.*;
import vn.edu.p2p.server.model.*;
import java.util.*;

/** Registry operations invoked under the TrackerService monitor. */
final class TrackerTransferService {
    private final TrackerContext context;
    private final TrackerAccess access;
    TrackerTransferService(TrackerContext context, TrackerAccess access) {
        this.context = context;
        this.access = access;
    }
    void report(String token, Progress input) {
        Account a = access.account(token, true); access.ownPeer(a, input.peerId(), true);
        SharedFile f = context.state.files.get(input.fileId());
        access.require(input.id() != null && input.id().matches("[a-zA-Z0-9-]{1,64}") && f != null
                && Set.of("DOWNLOADING", "WAITING", "PAUSED", "COMPLETED", "ERROR").contains(input.status())
                && input.bytes() >= 0 && input.bytes() <= f.manifest.size(), "Báo cáo tiến độ không hợp lệ.");
        String key = input.peerId() + ":" + input.id();
        context.state.transfers.put(key, new Transfer(input.id(), a.username, input.peerId(), input.fileId(),
                f.manifest.name(), input.status(), input.bytes(), System.currentTimeMillis()));
        while (context.state.transfers.size() > 1000) context.state.transfers.remove(context.state.transfers.keySet().iterator().next());
        context.store.save();
    }
}
