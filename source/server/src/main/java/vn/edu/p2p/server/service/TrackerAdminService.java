package vn.edu.p2p.server.service;

import vn.edu.p2p.server.dto.request.*;
import vn.edu.p2p.server.model.*;
import vn.edu.p2p.server.entity.*;
import java.util.*;
import org.springframework.stereotype.Service;
import vn.edu.p2p.server.dto.response.*;
import vn.edu.p2p.server.mapper.AccountMapper;
import vn.edu.p2p.server.mapper.PeerMapper;
import vn.edu.p2p.server.mapper.FileMapper;
import vn.edu.p2p.server.mapper.TransferMapper;
import vn.edu.p2p.server.mapper.AuditMapper;

/** Registry operations serialized on the shared context monitor. */
@Service
public class TrackerAdminService {
    private final TrackerContext context;
    private final TrackerAccess access;
    private final AccountMapper accountMapper;
    private final PeerMapper peerMapper;
    private final FileMapper fileMapper;
    private final TransferMapper transferMapper;
    private final AuditMapper auditMapper;

    public TrackerAdminService(
            TrackerContext context,
            TrackerAccess access,
            AccountMapper accountMapper,
            PeerMapper peerMapper,
            FileMapper fileMapper,
            TransferMapper transferMapper,
            AuditMapper auditMapper) {
        this.context = context;
        this.access = access;
        this.accountMapper = accountMapper;
        this.peerMapper = peerMapper;
        this.fileMapper = fileMapper;
        this.transferMapper = transferMapper;
        this.auditMapper = auditMapper;
    }

    public DashboardResponse dashboard(String token) {
        synchronized (context) {
            var transaction = context.beginTransaction();
            try {
                access.admin(token);
                return new DashboardResponse(
                        context.accounts.findAllByOrderByPositionAsc().stream()
                                .map(accountMapper::toResponse)
                                .toList(),
                        context.peers.findAllByOrderByPositionAsc().stream()
                                .map(p -> peerMapper.toResponse(p, access.online(p)))
                                .toList(),
                        context.files.findAllByOrderByPositionAsc().stream()
                                .map(f -> fileMapper.toResponse(f, access.onlineSources(f)))
                                .toList(),
                        context.transfers.findAllByOrderByPositionAsc().stream()
                                .map(transferMapper::toResponse)
                                .toList(),
                        context.audits.findAllByOrderByIdAsc().stream()
                                .map(auditMapper::toResponse)
                                .sorted(Comparator.comparingLong(Audit::time).reversed())
                                .limit(100)
                                .toList());
            } catch (Exception | Error failure) {
                context.rollback(transaction);
                throw failure;
            } finally {
                context.commit(transaction);
            }
        }
    }

    public void moderate(String token, String kind, String id, String status) {
        synchronized (context) {
            var transaction = context.beginTransaction();
            try {
                AccountEntity a = access.admin(token);
                access.require(
                        Set.of("APPROVED", "PENDING", "BLOCKED").contains(status),
                        "Trạng thái không hợp lệ.");
                switch (kind) {
                    case "accounts" -> {
                        AccountEntity target = context.accounts.findByUsername(id).orElse(null);
                        access.require(target != null, "Không có tài khoản.");
                        access.require(
                                !"ADMIN".equals(target.role),
                                "Không thay đổi trạng thái quản trị viên.");
                        target.status = status;
                    }
                    case "peers" -> {
                        PeerEntity p = context.peers.findPeerById(id).orElse(null);
                        access.require(p != null, "Không có peer.");
                        p.status = status;
                    }
                    case "files" -> {
                        FileEntity f = context.files.findFileById(id).orElse(null);
                        access.require(f != null, "Không có file.");
                        if ("APPROVED".equals(status)) {
                            access.require(f.malware == null || !f.malware.blocksApproval(),
                                    "File có cảnh báo mã độc hoặc đáng ngờ từ VirusTotal, không thể duyệt.");
                        }
                        f.status = status;
                    }
                    default -> throw new IllegalArgumentException("Loại đối tượng không hợp lệ.");
                }
                context.log(a.username, kind + "_" + status, id);
            } catch (Exception | Error failure) {
                context.rollback(transaction);
                throw failure;
            } finally {
                context.commit(transaction);
            }
        }
    }

    public void deleteFile(String token, String fileId) {
        synchronized (context) {
            var transaction = context.beginTransaction();
            try {
                var admin = access.admin(token);
                access.require(
                        fileId != null && fileId.matches("[a-f0-9]{64}"), "File ID không hợp lệ.");
                if (context.deletions.existsById(fileId)) return;
                var file = context.files.findFileById(fileId).orElse(null);
                access.require(file != null, "Không có file.");
                context.deletions.save(new FileDeletionEntity(fileId, System.currentTimeMillis()));
                context.files.delete(file);
                context.transfers.deleteByFileId(fileId);
                context.tickets.entrySet().removeIf(e -> fileId.equals(e.getValue().fileId()));
                context.log(admin.username, "DELETE_FILE_NETWORK", fileId);
            } catch (Exception | Error failure) {
                context.rollback(transaction);
                throw failure;
            } finally {
                context.commit(transaction);
            }
        }
    }
}
