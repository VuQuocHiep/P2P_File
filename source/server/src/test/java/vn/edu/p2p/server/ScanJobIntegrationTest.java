package vn.edu.p2p.server;

import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.p2p.common.model.Manifest;
import vn.edu.p2p.server.dto.request.*;
import vn.edu.p2p.server.dto.response.*;
import vn.edu.p2p.server.repository.FileRepository;
import vn.edu.p2p.server.service.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:scan-jobs;DB_CLOSE_DELAY=-1",
        "peermesh.admin.username=admin", "peermesh.admin.password=test-admin-secret-2026",
        "virustotal.worker-enabled=false"})
class ScanJobIntegrationTest {
    @Autowired TrackerAuthService auth;
    @Autowired TrackerPeerService peers;
    @Autowired TrackerFileService files;
    @Autowired TrackerAdminService adminService;
    @Autowired TrackerScanJobService jobs;
    @Autowired FileRepository repository;
    @MockitoBean VirusTotalClient vt;
    String admin, user, other, peer, otherPeer, id;
    Manifest manifest;
    byte[] bytes;

    @BeforeEach void setup() throws Exception {
        admin = auth.login(new Login("admin", "test-admin-secret-2026")).token();
        String username = "scan" + UUID.randomUUID().toString().substring(0, 8);
        auth.register(new Login(username, "password-123"));
        adminService.moderate(admin, "accounts", username, "APPROVED");
        user = auth.login(new Login(username, "password-123")).token();
        peer = peers.registerPeer(user, new RegisterPeer("", "source", "127.0.0.1", 19555)).id();
        adminService.moderate(admin, "peers", peer, "APPROVED");
        String username2 = "other" + UUID.randomUUID().toString().substring(0, 8);
        auth.register(new Login(username2, "password-123"));
        adminService.moderate(admin, "accounts", username2, "APPROVED");
        other = auth.login(new Login(username2, "password-123")).token();
        otherPeer = peers.registerPeer(other, new RegisterPeer("", "other", "127.0.0.1", 19556)).id();
        adminService.moderate(admin, "peers", otherPeer, "APPROVED");
        bytes = ("scan job fixture " + UUID.randomUUID()).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        id = Manifest.hash(bytes);
        manifest = new Manifest(id, "demo.pdf", bytes.length, Manifest.CHUNK_SIZE, List.of(id));
        files.advertise(user, new Advertisement(peer, manifest, List.of(0), false));
        when(vt.lookup(anyString())).thenReturn(MalwareReport.state("UNKNOWN", "No report", null));
    }

    void due() {
        var f = repository.findById(id).orElseThrow();
        f.scanNextAt = 0L;
        repository.save(f);
    }

    FileResponse file() {
        return adminService.dashboard(admin).files().stream().filter(f -> f.manifest().id().equals(id)).findFirst().orElseThrow();
    }

    void waitForSource() { jobs.request(admin, id); jobs.processNext(); }

    @Test void waitsForConsentThenVerifiesAndAutomaticallyCollectsReport() throws Exception {
        assertThrows(ResponseStatusException.class, () -> jobs.request(user, id));
        waitForSource();
        assertEquals("WAITING_SOURCE", file().malware().status());
        assertNull(jobs.claim(user, peer));
        adminService.moderate(admin, "files", id, "APPROVED"); // scanning remains optional
        files.advertise(user, new Advertisement(peer, manifest, List.of(0), true));
        due();
        var lease = jobs.claim(user, peer);
        assertNotNull(lease);
        assertNull(jobs.claim(user, peer)); // only one claimant
        var upload = new MockMultipartFile("file", bytes);
        assertThrows(ResponseStatusException.class, () -> jobs.upload(other, peer, id, lease.leaseToken(), upload));
        assertThrows(IllegalArgumentException.class, () -> jobs.upload(other, otherPeer, id, lease.leaseToken(), upload));
        assertThrows(IllegalArgumentException.class, () -> jobs.upload(user, peer, id, "invalid-lease", upload));
        assertThrows(IllegalArgumentException.class, () -> jobs.upload(user, peer, id, lease.leaseToken(),
                new MockMultipartFile("file", "x".repeat(bytes.length).getBytes())));
        verify(vt, never()).upload(any());
        when(vt.upload(any())).thenReturn(MalwareReport.state("QUEUED", "Queued", "job-1"));
        jobs.upload(user, peer, id, lease.leaseToken(), upload);
        assertEquals("ANALYSIS", file().scanStage());
        assertEquals("APPROVED", file().status());
        assertThrows(IllegalArgumentException.class, () -> jobs.upload(user, peer, id, lease.leaseToken(), upload));
        when(vt.poll(id, "job-1")).thenReturn(MalwareReport.state("READY", "Completed", "job-1"));
        due(); jobs.processNext();
        assertEquals("REPORT", file().scanStage());
        var detection = new MalwareReport("MALICIOUS", 1000, 900, 2, 0, 68, 0, 70,
                "Detected", null, List.of(new MalwareReport.Engine("Test", "malicious", "EICAR")));
        when(vt.lookup(id)).thenReturn(detection);
        due(); jobs.processNext();
        assertEquals("DONE", file().scanStage());
        assertEquals("BLOCKED", file().status());
        assertEquals(detection, repository.findById(id).orElseThrow().malware);
        assertThrows(IllegalArgumentException.class, () -> adminService.moderate(admin, "files", id, "APPROVED"));
    }

    @Test void handlesOfflineSourceExpiredLeaseRevocationAndCancellation() throws Exception {
        files.advertise(user, new Advertisement(peer, manifest, List.of(0), true));
        waitForSource();
        var persisted = repository.findById(id).orElseThrow();
        assertNotNull(persisted.scanRequestId); // durable, independent of admin token
        assertEquals("WAIT_SOURCE", persisted.scanStage);
        assertNull(jobs.claim(other, otherPeer)); // no source; leaves request waiting
        due();
        var first = jobs.claim(user, peer);
        persisted = repository.findById(id).orElseThrow();
        persisted.scanLeaseUntil = 1L;
        repository.save(persisted);
        var second = jobs.claim(user, peer);
        assertNotNull(second);
        assertNotEquals(first.leaseToken(), second.leaseToken());
        assertThrows(IllegalArgumentException.class, () -> jobs.upload(user, peer, id, first.leaseToken(), new MockMultipartFile("file", bytes)));
        files.advertise(user, new Advertisement(peer, manifest, List.of(0), false));
        assertThrows(IllegalArgumentException.class, () -> jobs.upload(user, peer, id, second.leaseToken(), new MockMultipartFile("file", bytes)));
        jobs.cancel(admin, id);
        assertEquals("CANCELLED", file().scanStage());
        assertNull(jobs.claim(user, peer));
        verify(vt, never()).upload(any());
    }

    @Test void existingReportAvoidsUploadingAndErrorsRetryWithoutApprovingAsClean() throws Exception {
        var clear = new MalwareReport("UNDETECTED", 1000, 900, 0, 0, 70, 0, 70, "No detections", null, List.of());
        when(vt.lookup(id)).thenReturn(clear);
        jobs.request(admin, id); jobs.processNext();
        assertEquals("DONE", file().scanStage());
        assertNull(jobs.claim(user, peer));
        verify(vt, never()).upload(any());
        when(vt.lookup(id)).thenThrow(new IllegalArgumentException("Test quota exceeded"));
        jobs.request(admin, id);
        for (int i = 0; i < 5; i++) { due(); jobs.processNext(); }
        assertEquals("ERROR", file().scanStage());
        assertEquals("SCAN_ERROR", file().malware().status());
        assertEquals("PENDING", file().status());
        adminService.moderate(admin, "files", id, "APPROVED");
    }
}
