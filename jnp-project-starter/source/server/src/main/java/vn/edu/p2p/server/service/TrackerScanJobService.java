package vn.edu.p2p.server.service;

import java.util.*;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.p2p.common.model.Manifest;
import vn.edu.p2p.server.dto.response.MalwareReport;
import vn.edu.p2p.server.entity.FileEntity;

/** Durable scan requests. No network IO is performed while holding the registry monitor. */
@Service
public class TrackerScanJobService {
    public record Lease(String fileId, String leaseToken, long expiresAt) {}
    private final TrackerContext context;
    private final TrackerAccess access;
    private final VirusTotalClient client;
    private final boolean workerEnabled;

    public TrackerScanJobService(TrackerContext context, TrackerAccess access, VirusTotalClient client,
            @Value("${virustotal.worker-enabled:true}") boolean workerEnabled) {
        this.context = context; this.access = access; this.client = client; this.workerEnabled = workerEnabled;
    }

    private <T> T transaction(Supplier<T> operation) {
        synchronized (context) {
            var tx = context.beginTransaction();
            try { return operation.get(); }
            catch (Exception | Error e) { context.rollback(tx); throw e; }
            finally { context.commit(tx); }
        }
    }

    private boolean active(FileEntity f) {
        return f.scanStage != null && !Set.of("DONE", "ERROR", "CANCELLED").contains(f.scanStage);
    }

    private void report(FileEntity f, MalwareReport value) {
        var old = f.malware;
        if (old != null && old.blocksApproval() && !value.permitsApproval() && !value.blocksApproval()) {
            value = new MalwareReport(value.status(), value.checkedAt(), old.analysisAt(), old.malicious(),
                    old.suspicious(), old.undetected(), old.harmless(), old.total(),
                    "Vẫn giữ cảnh báo lần trước. " + value.message(), value.analysisId(), old.engines());
        }
        f.malware = value;
        if (value.blocksApproval()) f.status = "BLOCKED";
    }

    public MalwareReport request(String token, String id) {
        return transaction(() -> {
            var admin = access.admin(token);
            var f = context.files.findFileById(id).orElseThrow(() -> new IllegalArgumentException("Không có file."));
            if (active(f)) return f.malware;
            access.require(!context.malwareChecks.contains(id), "File đang được kiểm tra.");
            f.scanStage = "LOOKUP";
            f.scanRequestId = UUID.randomUUID().toString();
            f.scanActor = admin.username;
            f.scanNextAt = System.currentTimeMillis();
            f.scanFailures = 0;
            clearLease(f);
            report(f, MalwareReport.state("WAITING_LOOKUP", "Đã yêu cầu quét. Server sẽ tra báo cáo SHA-256 và tự yêu cầu peer gửi file nếu cần.", null));
            context.log(admin.username, "REQUEST_VIRUSTOTAL_SCAN", id);
            return f.malware;
        });
    }

    public void cancel(String token, String id) {
        transaction(() -> {
            var admin = access.admin(token);
            var f = context.files.findFileById(id).orElseThrow(() -> new IllegalArgumentException("Không có file."));
            if (!active(f)) return null;
            f.scanStage = "CANCELLED";
            f.scanRequestId = UUID.randomUUID().toString();
            clearLease(f);
            report(f, MalwareReport.state("UNKNOWN", "Đã hủy yêu cầu. Nội dung đã gửi VirusTotal trước đó không thể thu hồi.", null));
            context.log(admin.username, "CANCEL_VIRUSTOTAL_SCAN", id);
            return null;
        });
    }

    public Lease claim(String token, String peerId) {
        return transaction(() -> {
            access.ownPeer(access.account(token, true), peerId, true);
            long now = System.currentTimeMillis();
            for (var f : context.files.findAllByOrderByPositionAsc()) {
                if (context.malwareChecks.contains(f.id)) continue;
                if ("UPLOADING".equals(f.scanStage) && f.scanLeaseUntil != null && f.scanLeaseUntil < now) {
                    clearLease(f);
                    f.scanStage = "WAIT_SOURCE";
                    report(f, MalwareReport.state("WAITING_SOURCE", "Peer gửi file bị gián đoạn. Đang chờ nguồn khác hoặc kết nối lại.", null));
                }
                if (!"WAIT_SOURCE".equals(f.scanStage) || !eligible(f, peerId)
                        || (f.scanNextAt != null && f.scanNextAt > now)) continue;
                f.scanStage = "UPLOADING";
                f.scanLeasePeer = peerId;
                f.scanLeaseToken = UUID.randomUUID().toString();
                f.scanLeaseUntil = now + 120_000;
                report(f, MalwareReport.state("UPLOADING", "Đang nhận file từ peer để gửi tới VirusTotal.", null));
                return new Lease(f.id, f.scanLeaseToken, f.scanLeaseUntil);
            }
            return null;
        });
    }

    private boolean eligible(FileEntity f, String peerId) {
        var chunks = f.holders.get(peerId);
        return f.scanConsentPeers.contains(peerId) && chunks != null
                && chunks.size() == f.manifest.hashes().size();
    }

    private FileEntity validateLease(String token, String peerId, String id, String lease) {
        access.ownPeer(access.account(token, true), peerId, true);
        var f = context.files.findFileById(id).orElseThrow(() -> new IllegalArgumentException("File đã bị xóa."));
        access.require("UPLOADING".equals(f.scanStage) && peerId.equals(f.scanLeasePeer)
                && lease != null && lease.equals(f.scanLeaseToken) && f.scanLeaseUntil != null
                && f.scanLeaseUntil >= System.currentTimeMillis(), "Yêu cầu gửi file đã hết hạn hoặc không thuộc peer này.");
        access.require(eligible(f, peerId), "Peer chưa cho phép quét hoặc không còn chia sẻ đủ file.");
        return f;
    }

    public MalwareReport upload(String token, String peerId, String id, String lease, MultipartFile upload) throws Exception {
        String requestId = transaction(() -> {
            var f = validateLease(token, peerId, id, lease);
            access.require(!context.malwareChecks.contains(id), "File đang được gửi quét.");
            access.require(upload.getSize() > 0 && upload.getSize() <= VirusTotalClient.MAX_UPLOAD
                    && upload.getSize() == f.manifest.size(), "Kích thước file gửi quét không hợp lệ (tối đa 32 MiB).");
            return f.scanRequestId;
        });
        byte[] bytes = upload.getBytes();
        access.require(id.equals(Manifest.hash(bytes)), "SHA-256 file không khớp với file đã chia sẻ.");
        transaction(() -> {
            validateLease(token, peerId, id, lease);
            access.require(!context.malwareChecks.contains(id), "File đang được gửi quét.");
            context.malwareChecks.add(id);
            return null;
        });
        try {
            var result = client.upload(bytes);
            return transaction(() -> {
                var f = context.files.findFileById(id).orElse(null);
                access.require(f != null && requestId.equals(f.scanRequestId), "Yêu cầu đã hủy hoặc file đã bị xóa.");
                f.scanStage = "ANALYSIS";
                f.scanFailures = 0;
                f.scanNextAt = System.currentTimeMillis() + 16_000;
                clearLease(f);
                report(f, result);
                context.log(f.scanActor, "VIRUSTOTAL_PEER_UPLOAD", id);
                return f.malware;
            });
        } catch (Exception error) {
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            transaction(() -> {
                var f = context.files.findFileById(id).orElse(null);
                if (f == null || !requestId.equals(f.scanRequestId)) return null;
                f.scanFailures = (f.scanFailures == null ? 0 : f.scanFailures) + 1;
                f.scanStage = f.scanFailures >= 5 ? "ERROR" : "WAIT_SOURCE";
                f.scanNextAt = System.currentTimeMillis() + 60_000;
                clearLease(f);
                String message = error instanceof IllegalArgumentException ? error.getMessage() : "Không gửi được file lên VirusTotal.";
                report(f, MalwareReport.state("SCAN_ERROR", message + ("ERROR".equals(f.scanStage)
                        ? " Đã dừng sau 5 lần lỗi; có thể yêu cầu quét lại." : " Sẽ thử lại sau khoảng 60 giây."), null));
                return null;
            });
            throw error;
        } finally { synchronized (context) { context.malwareChecks.remove(id); } }
    }

    public void release(String token, String peerId, String id, String lease) {
        transaction(() -> {
            var f = validateLease(token, peerId, id, lease);
            if (context.malwareChecks.contains(id)) return null;
            clearLease(f);
            f.scanStage = "WAIT_SOURCE";
            f.scanNextAt = System.currentTimeMillis() + 30_000;
            report(f, MalwareReport.state("WAITING_SOURCE", "Chưa nhận được file từ peer. Sẽ thử lại khi nguồn sẵn sàng và đã cho phép quét.", null));
            return null;
        });
    }

    private void clearLease(FileEntity f) { f.scanLeasePeer = null; f.scanLeaseToken = null; f.scanLeaseUntil = null; }

    private void expireLease(FileEntity f, long now) {
        if (!context.malwareChecks.contains(f.id) && "UPLOADING".equals(f.scanStage)
                && f.scanLeaseUntil != null && f.scanLeaseUntil < now) {
            clearLease(f);
            f.scanStage = "WAIT_SOURCE";
            report(f, MalwareReport.state("WAITING_SOURCE", "Nguồn gửi đã ngắt hoặc hết hạn. Đang chờ peer online và cho phép gửi file.", null));
        }
    }

    /** At most one VT call per tick, shared with the client's 15-second quota guard. */
    @Scheduled(fixedDelay = 16_000, initialDelay = 16_000)
    public void tick() {
        if (!workerEnabled) return;
        processNext();
    }

    public void processNext() {
        record Work(String id, String requestId, String stage, String analysisId) {}
        Work work = transaction(() -> {
            long now = System.currentTimeMillis();
            for (var f : context.files.findAllByOrderByPositionAsc()) {
                expireLease(f, now);
                if (f.scanStage == null || !Set.of("LOOKUP", "ANALYSIS", "REPORT").contains(f.scanStage)
                        || (f.scanNextAt != null && f.scanNextAt > now) || context.malwareChecks.contains(f.id)) continue;
                context.malwareChecks.add(f.id);
                return new Work(f.id, f.scanRequestId, f.scanStage, f.malware == null ? null : f.malware.analysisId());
            }
            return null;
        });
        if (work == null) return;
        try {
            var result = "ANALYSIS".equals(work.stage())
                    ? client.poll(work.id(), work.analysisId()) : client.lookup(work.id());
            transaction(() -> {
                var f = context.files.findFileById(work.id()).orElse(null);
                if (f == null || !work.requestId().equals(f.scanRequestId)) return null;
                f.scanFailures = 0;
                f.scanNextAt = System.currentTimeMillis() + 16_000;
                if ("LOOKUP".equals(work.stage()) && "UNKNOWN".equals(result.status())) {
                    if (f.manifest.size() <= 0 || f.manifest.size() > VirusTotalClient.MAX_UPLOAD) {
                        f.scanStage = "ERROR";
                        report(f, MalwareReport.state("SCAN_ERROR", "Chưa có báo cáo. Chỉ hỗ trợ gửi nội dung file tối đa 32 MiB; vẫn có thể duyệt thủ công.", null));
                    } else {
                        f.scanStage = "WAIT_SOURCE";
                        report(f, MalwareReport.state("WAITING_SOURCE", "Đang chờ nguồn trực tuyến có file hoàn chỉnh và quyền gửi tới VirusTotal.", null));
                    }
                } else {
                    f.scanStage = "QUEUED".equals(result.status()) ? "ANALYSIS"
                            : "READY".equals(result.status()) ? "REPORT" : "DONE";
                    report(f, result);
                    context.log(f.scanActor, "VIRUSTOTAL_" + result.status(), f.id);
                }
                return null;
            });
        } catch (Exception error) {
            if (error instanceof InterruptedException) Thread.currentThread().interrupt();
            transaction(() -> {
                var f = context.files.findFileById(work.id()).orElse(null);
                if (f == null || !work.requestId().equals(f.scanRequestId)) return null;
                f.scanFailures = (f.scanFailures == null ? 0 : f.scanFailures) + 1;
                if (f.scanFailures >= 5) f.scanStage = "ERROR";
                f.scanNextAt = System.currentTimeMillis() + 60_000;
                String message = error instanceof IllegalArgumentException ? error.getMessage() : "Không kết nối được VirusTotal.";
                report(f, MalwareReport.state("SCAN_ERROR", message + ("ERROR".equals(f.scanStage)
                        ? " Đã dừng sau 5 lần lỗi; có thể yêu cầu quét lại." : " Server sẽ thử lại sau khoảng 60 giây."), work.analysisId()));
                return null;
            });
        } finally { synchronized (context) { context.malwareChecks.remove(work.id()); } }
    }
}
