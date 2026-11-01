package vn.edu.p2p.server.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.p2p.common.model.Manifest;
import vn.edu.p2p.server.entity.RegistryEntity;
import vn.edu.p2p.server.model.*;
import vn.edu.p2p.server.migration.legacy.*;
import vn.edu.p2p.server.migration.LegacyRegistryMigration;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Import({LegacyRegistryMigration.class, TrackerRepositoryTest.JsonConfiguration.class})
class TrackerRepositoryTest {
    @TestConfiguration
    static class JsonConfiguration {
        @Bean
        ObjectMapper json() {
            return new ObjectMapper();
        }
    }

    @Autowired RegistryRepository legacy;
    @Autowired MigrationRepository migrations;
    @Autowired AccountRepository accounts;
    @Autowired PeerRepository peers;
    @Autowired FileRepository files;
    @Autowired TransferRepository transfers;
    @Autowired AuditRepository audits;
    @Autowired LegacyRegistryMigration migration;
    @Autowired TestEntityManager entityManager;
    @Autowired ObjectMapper json;

    private Snapshot snapshot() {
        var snapshot = new Snapshot();
        snapshot.accounts.put("zed", new Account("zed", "old-hash", "USER", "APPROVED"));
        snapshot.accounts.put("alice", new Account("alice", "password-hash", "USER", "APPROVED"));
        var peer = new Peer();
        peer.id = "peer-a";
        peer.owner = "alice";
        peer.name = "Laptop";
        peer.host = "127.0.0.1";
        peer.port = 19001;
        peer.status = "APPROVED";
        peer.lastSeen = 123;
        snapshot.peers.put(peer.id, peer);
        byte[] bytes = {1, 2, 3};
        String id = Manifest.hash(bytes);
        var file = new SharedFile();
        file.manifest =
                new Manifest(id, "sample.bin", bytes.length, Manifest.CHUNK_SIZE, List.of(id));
        file.status = "APPROVED";
        file.submittedBy = "alice";
        file.holders.put(peer.id, List.of(0));
        snapshot.files.put(id, file);
        snapshot.transfers.put(
                "peer-a:job",
                new Transfer("job", "alice", peer.id, id, "sample.bin", "COMPLETED", 3, 456));
        snapshot.audit.add(new Audit(1, "alice", "SUBMIT_FILE", id));
        return snapshot;
    }

    @Test
    void importsAllLegacyDataOnceAndPreservesInsertionOrder() throws Exception {
        var snapshot = snapshot();
        String payload = json.writeValueAsString(snapshot);
        legacy.saveAndFlush(new RegistryEntity(1, payload));
        migration.migrate();
        entityManager.clear();
        assertEquals(
                List.of("zed", "alice"),
                accounts.findAllByOrderByPositionAsc().stream().map(a -> a.username).toList());
        assertEquals("password-hash", accounts.findByUsername("alice").orElseThrow().passwordHash);
        assertEquals(123, peers.findById("peer-a").orElseThrow().lastSeen);
        var originalFile = snapshot.files.values().iterator().next();
        var file = files.findById(originalFile.manifest.id()).orElseThrow();
        assertEquals(originalFile.manifest, file.manifest);
        assertEquals(List.of(0), file.holders.get("peer-a"));
        var transfer = transfers.findById("peer-a:job").orElseThrow();
        assertEquals("job", transfer.id);
        assertEquals(3, transfer.bytes);
        assertEquals("COMPLETED", transfer.status);
        assertEquals("SUBMIT_FILE", audits.findAllByOrderByIdAsc().get(0).action);
        assertEquals(payload, legacy.findById(1).orElseThrow().getPayload());
        migration.migrate();
        assertEquals(2, accounts.count());
        assertEquals(1, audits.count());
        assertTrue(migrations.existsById(LegacyRegistryMigration.MIGRATION));
    }

    @Test
    void persistsHolderChangesAndRetainsLegacyBackupAfterMigration() throws Exception {
        legacy.saveAndFlush(new RegistryEntity(1, json.writeValueAsString(snapshot())));
        migration.migrate();
        entityManager.clear();
        var file = files.findAll().get(0);
        file.holders.clear();
        files.saveAndFlush(file);
        var account = accounts.findByUsername("alice").orElseThrow();
        account.status = "BLOCKED";
        accounts.saveAndFlush(account);
        entityManager.clear();
        assertTrue(files.findById(file.id).orElseThrow().holders.isEmpty());
        assertEquals("BLOCKED", accounts.findByUsername("alice").orElseThrow().status);
        migration.migrate();
        assertEquals("BLOCKED", accounts.findByUsername("alice").orElseThrow().status);
        assertTrue(legacy.existsById(1));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void failedImportRollsBackEveryDomainTableAndKeepsLegacyPayload() throws Exception {
        var snapshot = snapshot();
        snapshot.peers.values().iterator().next().id = null;
        String payload = json.writeValueAsString(snapshot);
        legacy.saveAndFlush(new RegistryEntity(1, payload));
        try {
            assertThrows(Exception.class, migration::migrate);
            assertEquals(0, accounts.count());
            assertEquals(0, peers.count());
            assertEquals(0, files.count());
            assertEquals(0, transfers.count());
            assertEquals(0, audits.count());
            assertFalse(migrations.existsById(LegacyRegistryMigration.MIGRATION));
            assertEquals(payload, legacy.findById(1).orElseThrow().getPayload());
        } finally {
            legacy.deleteAll();
        }
    }
}
