package vn.edu.p2p.server.service;

import vn.edu.p2p.server.dto.request.*;
import vn.edu.p2p.server.model.*;
import vn.edu.p2p.server.entity.*;
import java.util.*;
import org.springframework.stereotype.Service;
import vn.edu.p2p.server.dto.response.*;

/** Registry operations serialized on the shared context monitor. */
@Service
public class TrackerTransferService {
    private final TrackerContext context;
    private final TrackerAccess access;

    public TrackerTransferService(TrackerContext context, TrackerAccess access) {
        this.context = context;
        this.access = access;
    }

    public void report(String token, Progress input) {
        synchronized (context) {
            var transaction = context.beginTransaction();
            try {
                AccountEntity a = access.account(token, true);
                access.ownPeer(a, input.peerId(), true);
                FileEntity f = context.files.findFileById(input.fileId()).orElse(null);
                access.require(
                        input.id() != null
                                && input.id().matches("[a-zA-Z0-9-]{1,64}")
                                && f != null
                                && Set.of("DOWNLOADING", "WAITING", "PAUSED", "COMPLETED", "ERROR")
                                        .contains(input.status())
                                && input.bytes() >= 0
                                && input.bytes() <= f.manifest.size(),
                        "Báo cáo tiến độ không hợp lệ.");
                String key = input.peerId() + ":" + input.id();
                var transfer =
                        new TransferEntity(
                                key,
                                input.id(),
                                a.username,
                                input.peerId(),
                                input.fileId(),
                                f.manifest.name(),
                                input.status(),
                                input.bytes(),
                                System.currentTimeMillis());
                transfer.position =
                        context.transfers
                                .findById(key)
                                .map(old -> old.position)
                                .orElseGet(() -> context.nextTransferPosition++);
                context.transfers.save(transfer);
                while (context.transfers.count() > 1000)
                    context.transfers
                            .findFirstByOrderByPositionAsc()
                            .ifPresent(context.transfers::delete);
            } catch (Exception | Error failure) {
                context.rollback(transaction);
                throw failure;
            } finally {
                context.commit(transaction);
            }
        }
    }
}
