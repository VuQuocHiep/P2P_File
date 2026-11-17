package vn.edu.p2p.client.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;
import org.springframework.stereotype.Service;

/** Applies durable tracker tombstones to this peer's managed copies. */
@Service
public class PeerDeletionService {
    private final PeerContext context;

    public PeerDeletionService(PeerContext context) {
        this.context = context;
    }

    void synchronize() throws Exception {
        var deleted = context.tracker.call("GET", "/api/files/deletions", null);
        if (!deleted.isArray()) return;
        var ids = new ArrayList<String>();
        for (var value : deleted) {
            String id = value.asText();
            if (id.matches("[a-f0-9]{64}")) ids.add(id);
        }
        apply(ids);
    }

    void apply(List<String> ids) {
        synchronized (context.storage.monitor()) {
            boolean changed = false;
            for (String id : ids) {
                boolean first = context.storage.markDeleted(id);
                var entry = context.library.findByFileId(id);
                boolean hadJobs =
                        context.jobs.findAll().stream().anyMatch(j -> id.equals(j.fileId));
                if (!first && entry == null && !hadJobs) continue;
                changed = true;
                if (entry != null) entry.sharing = false;
                for (var job : new ArrayList<>(context.jobs.findAll())) {
                    if (!id.equals(job.fileId)) continue;
                    job.status = "PAUSED";
                    var workers = context.downloadPools.get(job.id);
                    if (workers != null) {
                        // shutdownNow drains pending tasks without completing their Futures.
                        // Cancel them so the coordinator can release its slot for the next file.
                        for (Runnable pending : workers.shutdownNow())
                            if (pending instanceof Future<?> future) future.cancel(false);
                    }
                }
                context.jobs.deleteByFileId(id);
                context.library.deleteByFileId(id);
                if (entry != null || hadJobs)
                    context.events.append(
                            "Quản trị viên đã xóa file toàn mạng: "
                                    + (entry != null ? entry.manifest.name() : id));
            }
            if (changed) context.storage.saveCheckpoint();
        }
        retryPending();
    }

    void retryPending() {
        for (String id : context.storage.pendingDeletions()) {
            try {
                context.storage.deleteManagedFile(id);
            } catch (IOException e) {
                context.connection = "Đang chờ xóa bản lưu của file: " + id.substring(0, 8);
            }
        }
    }
}
