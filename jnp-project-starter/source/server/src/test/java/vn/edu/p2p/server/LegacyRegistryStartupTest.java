package vn.edu.p2p.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.p2p.server.dto.request.Login;
import vn.edu.p2p.server.migration.legacy.Account;
import vn.edu.p2p.server.migration.legacy.Peer;
import vn.edu.p2p.server.migration.legacy.Snapshot;
import vn.edu.p2p.server.repository.AccountRepository;
import vn.edu.p2p.server.repository.MigrationRepository;
import vn.edu.p2p.server.repository.PeerRepository;
import vn.edu.p2p.server.repository.RegistryRepository;
import vn.edu.p2p.server.service.TrackerAdminService;
import vn.edu.p2p.server.service.TrackerAuthService;

import java.sql.DriverManager;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LegacyRegistryStartupTest {
    @Test
    void migratesExistingDatabaseAndKeepsDomainUpdatesAfterRestart() throws Exception {
        String url = "jdbc:h2:mem:migration-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1";
        var snapshot = new Snapshot();
        snapshot.accounts.put(
                "admin",
                new Account(
                        "admin",
                        new BCryptPasswordEncoder().encode("test-admin-secret-2026"),
                        "ADMIN",
                        "APPROVED"));
        snapshot.accounts.put(
                "alice", new Account("alice", "legacy-password-hash", "USER", "APPROVED"));
        var peer = new Peer();
        peer.id = "legacy-peer";
        peer.owner = "alice";
        peer.name = "Laptop";
        peer.host = "127.0.0.1";
        peer.port = 19001;
        peer.status = "APPROVED";
        peer.lastSeen = System.currentTimeMillis();
        snapshot.peers.put(peer.id, peer);
        String payload = new ObjectMapper().writeValueAsString(snapshot);
        try (var connection = DriverManager.getConnection(url, "sa", "")) {
            connection
                    .createStatement()
                    .execute("CREATE TABLE registry (id INT PRIMARY KEY, payload CLOB NOT NULL)");
            try (var statement =
                    connection.prepareStatement("INSERT INTO registry (id,payload) VALUES (1,?)")) {
                statement.setString(1, payload);
                statement.executeUpdate();
            }
        }
        String[] args = {
            "--server.port=0",
            "--spring.datasource.url=" + url,
            "--spring.datasource.password=",
            "--peermesh.admin.username=admin",
            "--peermesh.admin.password=test-admin-secret-2026"
        };
        String oldToken;
        String deletedId = vn.edu.p2p.common.model.Manifest.hash(new byte[] {9, 8, 7});
        try {
            try (var app = new SpringApplicationBuilder(TrackerApplication.class).run(args)) {
                var auth = app.getBean(TrackerAuthService.class);
                oldToken = auth.login(new Login("admin", "test-admin-secret-2026")).token();
                assertEquals("ADMIN", auth.me(oldToken).role());
                assertEquals(
                        0,
                        app.getBean(PeerRepository.class).findById(peer.id).orElseThrow().lastSeen);
                app.getBean(TrackerAdminService.class)
                        .moderate(oldToken, "accounts", "alice", "BLOCKED");
                auth.register(new Login("after-migration", "password-123"));
                app.getBean(vn.edu.p2p.server.repository.FileDeletionRepository.class)
                        .saveAndFlush(
                                new vn.edu.p2p.server.entity.FileDeletionEntity(deletedId, 123));
                assertEquals(
                        payload,
                        app.getBean(RegistryRepository.class)
                                .findById(1)
                                .orElseThrow()
                                .getPayload());
            }
            try (var app = new SpringApplicationBuilder(TrackerApplication.class).run(args)) {
                var accounts = app.getBean(AccountRepository.class);
                assertEquals("BLOCKED", accounts.findByUsername("alice").orElseThrow().status);
                assertEquals(
                        "legacy-password-hash",
                        accounts.findByUsername("alice").orElseThrow().passwordHash);
                assertTrue(accounts.existsByUsername("after-migration"));
                assertEquals(1, app.getBean(MigrationRepository.class).count());
                assertTrue(
                        app.getBean(vn.edu.p2p.server.repository.FileDeletionRepository.class)
                                .existsById(deletedId));
                assertEquals(
                        payload,
                        app.getBean(RegistryRepository.class)
                                .findById(1)
                                .orElseThrow()
                                .getPayload());
                assertThrows(
                        ResponseStatusException.class,
                        () -> app.getBean(TrackerAuthService.class).me(oldToken));
            }
        } finally {
            try (var connection = DriverManager.getConnection(url, "sa", "")) {
                connection.createStatement().execute("SHUTDOWN");
            }
        }
    }
}
