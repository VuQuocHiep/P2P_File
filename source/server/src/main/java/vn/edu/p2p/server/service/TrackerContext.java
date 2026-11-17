package vn.edu.p2p.server.service;

import vn.edu.p2p.server.repository.*;
import vn.edu.p2p.server.migration.LegacyRegistryMigration;
import vn.edu.p2p.server.entity.*;
import vn.edu.p2p.server.model.Session;
import vn.edu.p2p.server.model.Ticket;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import java.util.*;

/** Shared monitor stays locked until each JPA transaction commits or rolls back. */
@Component
final class TrackerContext {
    final AccountRepository accounts;
    final PeerRepository peers;
    final FileRepository files;
    final TransferRepository transfers;
    final AuditRepository audits;
    final FileDeletionRepository deletions;
    private final PlatformTransactionManager transactionManager;
    final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();
    final Map<String, Session> sessions = new HashMap<>();
    final Map<String, Ticket> tickets = new HashMap<>();
    final Set<String> malwareChecks = new HashSet<>();
    long nextAccountPosition, nextPeerPosition, nextFilePosition, nextTransferPosition;

    TrackerContext(
            AccountRepository accounts,
            PeerRepository peers,
            FileRepository files,
            TransferRepository transfers,
            AuditRepository audits,
            FileDeletionRepository deletions,
            LegacyRegistryMigration migration,
            PlatformTransactionManager transactionManager,
            @Value("${peermesh.admin.username}") String admin,
            @Value("${peermesh.admin.password}") String password)
            throws Exception {
        this.accounts = accounts;
        this.peers = peers;
        this.files = files;
        this.transfers = transfers;
        this.audits = audits;
        this.deletions = deletions;
        this.transactionManager = transactionManager;
        migration.migrate();
        var transaction = beginTransaction();
        try {
            peers.resetLastSeen();
            nextAccountPosition =
                    accounts.findFirstByOrderByPositionDesc().map(a -> a.position + 1).orElse(0L);
            nextPeerPosition =
                    peers.findFirstByOrderByPositionDesc().map(p -> p.position + 1).orElse(0L);
            nextFilePosition =
                    files.findFirstByOrderByPositionDesc().map(f -> f.position + 1).orElse(0L);
            nextTransferPosition =
                    transfers.findFirstByOrderByPositionDesc().map(t -> t.position + 1).orElse(0L);
            if (accounts.findAllByOrderByPositionAsc().stream()
                    .noneMatch(a -> "ADMIN".equals(a.role))) {
                TrackerAccess.checkName(admin);
                TrackerAccess.require(
                        password.length() >= 12 && password.length() <= 72,
                        "Lần chạy đầu cần ADMIN_PASSWORD dài 12–72 ký tự.");
                var account =
                        new AccountEntity(admin, passwords.encode(password), "ADMIN", "APPROVED");
                account.position = nextAccountPosition++;
                accounts.save(account);
                log(admin, "BOOTSTRAP_ADMIN", admin);
            }
        } catch (Exception | Error failure) {
            rollback(transaction);
            throw failure;
        } finally {
            commit(transaction);
        }
    }

    TransactionStatus beginTransaction() {
        return transactionManager.getTransaction(new DefaultTransactionDefinition());
    }

    void rollback(TransactionStatus transaction) {
        transactionManager.rollback(transaction);
    }

    void commit(TransactionStatus transaction) {
        if (!transaction.isCompleted()) transactionManager.commit(transaction);
    }

    void log(String actor, String action, String target) {
        audits.save(new AuditEntity(System.currentTimeMillis(), actor, action, target));
        if (audits.count() > 2000) audits.findFirstByOrderByIdAsc().ifPresent(audits::delete);
    }
}
