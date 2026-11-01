package vn.edu.p2p.server.migration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.p2p.server.entity.*;
import vn.edu.p2p.server.migration.legacy.Snapshot;
import vn.edu.p2p.server.repository.*;

@Service
public class LegacyRegistryMigration {
    public static final String MIGRATION = "registry-json-to-domain-tables-v1";
    private final RegistryRepository legacy;
    private final MigrationRepository migrations;
    private final AccountRepository accounts;
    private final PeerRepository peers;
    private final FileRepository files;
    private final TransferRepository transfers;
    private final AuditRepository audits;
    private final ObjectMapper json;

    public LegacyRegistryMigration(
            RegistryRepository legacy,
            MigrationRepository migrations,
            AccountRepository accounts,
            PeerRepository peers,
            FileRepository files,
            TransferRepository transfers,
            AuditRepository audits,
            ObjectMapper json) {
        this.legacy = legacy;
        this.migrations = migrations;
        this.accounts = accounts;
        this.peers = peers;
        this.files = files;
        this.transfers = transfers;
        this.audits = audits;
        this.json = json;
    }

    @Transactional(rollbackFor = Exception.class)
    public void migrate() throws Exception {
        if (migrations.existsById(MIGRATION)) return;
        var row = legacy.findById(1);
        if (row.isPresent()) {
            if (accounts.count()
                            + peers.count()
                            + files.count()
                            + transfers.count()
                            + audits.count()
                    != 0)
                throw new IllegalStateException(
                        "Cannot import legacy registry into nonempty domain tables without a migration marker.");
            Snapshot snapshot = json.readValue(row.get().getPayload(), Snapshot.class);
            long position = 0;
            for (var old : snapshot.accounts.values()) {
                var entity =
                        new AccountEntity(old.username, old.passwordHash, old.role, old.status);
                entity.created = old.created;
                entity.position = position++;
                accounts.save(entity);
            }
            position = 0;
            for (var old : snapshot.peers.values()) {
                var entity = new PeerEntity();
                entity.id = old.id;
                entity.owner = old.owner;
                entity.name = old.name;
                entity.host = old.host;
                entity.port = old.port;
                entity.status = old.status;
                entity.lastSeen = old.lastSeen;
                entity.position = position++;
                peers.save(entity);
            }
            position = 0;
            for (var old : snapshot.files.values()) {
                var entity = new FileEntity();
                entity.id = old.manifest.id();
                entity.manifest = old.manifest;
                entity.status = old.status;
                entity.submittedBy = old.submittedBy;
                entity.holders = old.holders;
                entity.position = position++;
                files.save(entity);
            }
            position = 0;
            for (var entry : snapshot.transfers.entrySet()) {
                var old = entry.getValue();
                var entity =
                        new TransferEntity(
                                entry.getKey(),
                                old.id(),
                                old.owner(),
                                old.peerId(),
                                old.fileId(),
                                old.name(),
                                old.status(),
                                old.bytes(),
                                old.time());
                entity.position = position++;
                transfers.save(entity);
            }
            for (var old : snapshot.audit)
                audits.save(new AuditEntity(old.time(), old.actor(), old.action(), old.target()));
        }
        migrations.saveAndFlush(new MigrationEntity(MIGRATION));
    }
}
