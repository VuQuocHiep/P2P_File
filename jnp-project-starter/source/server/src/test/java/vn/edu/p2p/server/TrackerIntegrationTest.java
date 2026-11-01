package vn.edu.p2p.server;

import vn.edu.p2p.server.service.*;
import vn.edu.p2p.server.dto.response.LoginResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.p2p.common.model.Manifest;
import vn.edu.p2p.server.dto.request.*;
import java.net.*;
import java.net.http.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.datasource.url=jdbc:h2:mem:tracker-tests;DB_CLOSE_DELAY=-1",
            "peermesh.admin.username=admin",
            "virustotal.worker-enabled=false",
            "peermesh.admin.password=test-admin-secret-2026"
        })
class TrackerIntegrationTest {
    @Autowired TrackerAuthService authService;
    @Autowired TrackerPeerService peerService;
    @Autowired TrackerFileService fileService;
    @Autowired TrackerTicketService ticketService;
    @Autowired TrackerTransferService transferService;
    @Autowired TrackerAdminService adminService;
    @Autowired TrackerMalwareService malwareService;
    @org.springframework.test.context.bean.override.mockito.MockitoBean VirusTotalClient virusTotal;
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    String admin;

    @BeforeEach
    void login() throws Exception {
        admin = token(authService.login(new Login("admin", "test-admin-secret-2026")));
        org.mockito.Mockito.when(virusTotal.lookup(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(new vn.edu.p2p.server.dto.response.MalwareReport(
                        "UNDETECTED", System.currentTimeMillis(), 1000, 0, 0, 70, 0, 70,
                        "Test fixture: no detections", null, List.of()));
    }

    void approveFile(String id) {
        malwareService.scan(admin, id, null, false);
        adminService.moderate(admin, "files", id, "APPROVED");
    }

    @Test
    void virusTotalBlocksApprovalPersistsEvidenceAndVerifiesUploads() throws Exception {
        String username = user("scan");
        String userToken = token(authService.login(new Login(username, "password-123")));
        adminService.moderate(admin, "accounts", username, "APPROVED");
        String peer = peerService.registerPeer(userToken,
                new RegisterPeer("", "Scan laptop", "127.0.0.1", 19015)).id();
        adminService.moderate(admin, "peers", peer, "APPROVED");
        byte[] bytes = "harmless integration scan fixture".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String id = Manifest.hash(bytes);
        var manifest = new Manifest(id, "scan-demo.txt", bytes.length, Manifest.CHUNK_SIZE, List.of(id));
        fileService.advertise(userToken, new Advertisement(peer, manifest, List.of(0)));
        adminService.moderate(admin, "files", id, "APPROVED");
        assertNull(adminService.dashboard(admin).files().stream()
                .filter(f -> f.manifest().id().equals(id)).findFirst().orElseThrow().malware());
        org.mockito.Mockito.verifyNoInteractions(virusTotal);
        assertThrows(ResponseStatusException.class,
                () -> malwareService.scan(userToken, id, null, false));

        var unknown = vn.edu.p2p.server.dto.response.MalwareReport.state("UNKNOWN", "No data", null);
        org.mockito.Mockito.when(virusTotal.lookup(id)).thenReturn(unknown);
        malwareService.scan(admin, id, null, false);
        assertEquals("APPROVED", adminService.dashboard(admin).files().stream()
                .filter(f -> f.manifest().id().equals(id)).findFirst().orElseThrow().status());
        adminService.moderate(admin, "files", id, "PENDING");
        adminService.moderate(admin, "files", id, "APPROVED");
        var wrong = new org.springframework.mock.web.MockMultipartFile(
                "file", "wrong.txt", "text/plain", "x".repeat(bytes.length).getBytes());
        assertThrows(IllegalArgumentException.class,
                () -> malwareService.scan(admin, id, wrong, true));
        var correct = new org.springframework.mock.web.MockMultipartFile("file", bytes);
        assertThrows(IllegalArgumentException.class,
                () -> malwareService.scan(admin, id, correct, false));
        org.mockito.Mockito.verify(virusTotal, org.mockito.Mockito.never())
                .upload(org.mockito.ArgumentMatchers.any());

        var queued = vn.edu.p2p.server.dto.response.MalwareReport.state("QUEUED", "Queued", "job123");
        org.mockito.Mockito.when(virusTotal.upload(org.mockito.ArgumentMatchers.any())).thenReturn(queued);
        assertEquals("QUEUED", malwareService.scan(admin, id, correct, true).status());
        assertEquals("APPROVED", adminService.dashboard(admin).files().stream()
                .filter(f -> f.manifest().id().equals(id)).findFirst().orElseThrow().status());
        org.mockito.Mockito.when(virusTotal.poll(id, "job123"))
                .thenReturn(vn.edu.p2p.server.dto.response.MalwareReport.state("READY", "Ready", "job123"));
        assertEquals("READY", malwareService.scan(admin, id, null, false).status());

        var clear = new vn.edu.p2p.server.dto.response.MalwareReport(
                "UNDETECTED", 1234, 1000, 0, 0, 70, 0, 70, "Test clear", null, List.of());
        org.mockito.Mockito.when(virusTotal.lookup(id)).thenReturn(clear);
        malwareService.scan(admin, id, null, false);
        adminService.moderate(admin, "files", id, "APPROVED");
        assertTrue(fileService.files(userToken, "scan-demo").stream().anyMatch(f -> f.manifest().id().equals(id)));

        var infected = new vn.edu.p2p.server.dto.response.MalwareReport(
                "MALICIOUS", 2345, 2000, 3, 0, 67, 0, 70, "Test detection", null,
                List.of(new vn.edu.p2p.server.dto.response.MalwareReport.Engine("Test engine", "malicious", "Test.EICAR")));
        org.mockito.Mockito.when(virusTotal.lookup(id)).thenReturn(infected);
        malwareService.scan(admin, id, null, false);
        var saved = adminService.dashboard(admin).files().stream()
                .filter(f -> f.manifest().id().equals(id)).findFirst().orElseThrow();
        assertEquals("BLOCKED", saved.status());
        assertEquals(infected, saved.malware());
        assertTrue(fileService.files(userToken, "scan-demo").isEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> adminService.moderate(admin, "files", id, "APPROVED"));
        org.mockito.Mockito.when(virusTotal.lookup(id)).thenReturn(unknown);
        assertTrue(malwareService.scan(admin, id, null, false).blocksApproval());
        assertThrows(IllegalArgumentException.class,
                () -> adminService.moderate(admin, "files", id, "APPROVED"));
    }

    String token(LoginResponse login) {
        return login.token();
    }

    String user(String prefix) {
        String username = prefix + UUID.randomUUID().toString().substring(0, 8);
        authService.register(new Login(username, "password-123"));
        return username;
    }

    @Test
    void approvalsTicketsRevocationAndSourceOwnership() throws Exception {
        String alice = user("alice"), bob = user("bob");
        String a = token(authService.login(new Login(alice, "password-123"))),
                b = token(authService.login(new Login(bob, "password-123")));
        assertThrows(ResponseStatusException.class, () -> fileService.files(a, ""));
        assertThrows(ResponseStatusException.class, () -> adminService.dashboard(a));
        adminService.moderate(admin, "accounts", alice, "APPROVED");
        adminService.moderate(admin, "accounts", bob, "APPROVED");
        String peer =
                peerService
                        .registerPeer(a, new RegisterPeer("", "Laptop", "127.0.0.1", 19001))
                        .id();
        byte[] bytes =
                "content for verified transfer".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String hash = Manifest.hash(bytes);
        var m = new Manifest(hash, "test.bin", bytes.length, Manifest.CHUNK_SIZE, List.of(hash));
        assertThrows(
                ResponseStatusException.class,
                () -> fileService.advertise(a, new Advertisement(peer, m, List.of(0))));
        adminService.moderate(admin, "peers", peer, "APPROVED");
        fileService.advertise(a, new Advertisement(peer, m, List.of(0)));
        assertTrue(fileService.files(b, "test.bin").isEmpty());
        approveFile(hash);
        assertEquals(1, fileService.sources(b, hash).size());
        String ticket = ticketService.ticket(b, new TicketRequest(hash, peer)).ticket();
        assertEquals(
                true, ticketService.authorize(a, new CheckTicket(ticket, peer, hash)).allowed());
        assertThrows(
                ResponseStatusException.class,
                () -> ticketService.authorize(b, new CheckTicket(ticket, peer, hash)));
        adminService.moderate(admin, "accounts", bob, "BLOCKED");
        assertThrows(
                ResponseStatusException.class,
                () -> ticketService.authorize(a, new CheckTicket(ticket, peer, hash)));
        adminService.moderate(admin, "accounts", bob, "APPROVED");
        adminService.moderate(admin, "files", hash, "BLOCKED");
        assertThrows(
                ResponseStatusException.class,
                () -> ticketService.authorize(a, new CheckTicket(ticket, peer, hash)));
        approveFile(hash);
        fileService.unshare(a, peer, hash);
        assertTrue(fileService.sources(b, hash).isEmpty());
        assertThrows(
                ResponseStatusException.class,
                () -> ticketService.authorize(a, new CheckTicket(ticket, peer, hash)));
    }

    @Test
    void browserMutationsNeedCustomHeaderAndSession() throws Exception {
        var http = HttpClient.newHttpClient();
        var request =
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/auth/login"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString("{}"));
        assertEquals(
                403, http.send(request.build(), HttpResponse.BodyHandlers.ofString()).statusCode());
        var dashboard =
                HttpRequest.newBuilder(
                                URI.create("http://127.0.0.1:" + port + "/api/admin/dashboard"))
                        .build();
        assertEquals(401, http.send(dashboard, HttpResponse.BodyHandlers.ofString()).statusCode());
    }

    @Test
    void responseDtosKeepHttpFieldsAndSessionAuthentication() throws Exception {
        var http =
                HttpClient.newBuilder()
                        .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                        .build();
        String username = "http-" + UUID.randomUUID().toString().substring(0, 8);
        var registration =
                send(http, "POST", "/auth/register", new Login(username, "password-123"));
        assertEquals(200, registration.statusCode());
        assertFields(json.readTree(registration.body()), "message", "status");

        var login = send(http, "POST", "/auth/login", new Login(username, "password-123"));
        assertEquals(200, login.statusCode());
        var loggedIn = json.readTree(login.body());
        assertFields(loggedIn, "token", "user");
        assertFields(loggedIn.path("user"), "username", "role", "status", "created");
        assertEquals("PENDING", loggedIn.path("user").path("status").asText());
        assertEquals(
                loggedIn.path("user"), json.readTree(send(http, "GET", "/auth/me", null).body()));

        adminService.moderate(admin, "accounts", username, "APPROVED");
        var peer = send(http, "POST", "/peers", new RegisterPeer("", "Laptop", "127.0.0.1", 19002));
        assertEquals(200, peer.statusCode());
        var peerJson = json.readTree(peer.body());
        assertFields(
                peerJson, "id", "owner", "name", "host", "port", "status", "online", "lastSeen");
        String peerId = peerJson.path("id").asText();
        adminService.moderate(admin, "peers", peerId, "APPROVED");

        byte[] bytes = "http response contract".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String id = Manifest.hash(bytes);
        var manifest =
                new Manifest(id, "contract.bin", bytes.length, Manifest.CHUNK_SIZE, List.of(id));
        var file = send(http, "POST", "/files", new Advertisement(peerId, manifest, List.of(0)));
        assertEquals(200, file.statusCode());
        assertFields(
                json.readTree(file.body()),
                "manifest",
                "status",
                "submittedBy",
                "sources",
                "holders", "malware", "scanStage");
        approveFile(id);
        var sources = json.readTree(send(http, "GET", "/files/" + id + "/sources", null).body());
        assertEquals(1, sources.size());
        assertFields(
                sources.get(0),
                "id",
                "owner",
                "name",
                "host",
                "port",
                "status",
                "online",
                "lastSeen",
                "chunks");
        assertEquals(json.readTree("[0]"), sources.get(0).path("chunks"));

        var ticket =
                json.readTree(send(http, "POST", "/tickets", new TicketRequest(id, peerId)).body());
        assertFields(ticket, "ticket", "expiresIn");
        var allowed =
                send(
                        http,
                        "POST",
                        "/tickets/authorize",
                        new CheckTicket(ticket.path("ticket").asText(), peerId, id));
        assertEquals(json.readTree("{\"allowed\":true}"), json.readTree(allowed.body()));
        var progress =
                send(
                        http,
                        "POST",
                        "/transfers",
                        new Progress("contract", peerId, id, "COMPLETED", bytes.length));
        assertEquals(json.readTree("{\"ok\":true}"), json.readTree(progress.body()));

        assertEquals(200, send(http, "POST", "/auth/logout", null).statusCode());
        assertEquals(401, send(http, "GET", "/auth/me", null).statusCode());
    }

    private HttpResponse<String> send(HttpClient http, String method, String path, Object body)
            throws Exception {
        var request =
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api" + path))
                        .header("X-PeerMesh", "1")
                        .header("Content-Type", "application/json")
                        .method(
                                method,
                                body == null
                                        ? HttpRequest.BodyPublishers.noBody()
                                        : HttpRequest.BodyPublishers.ofString(
                                                json.writeValueAsString(body)));
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private void assertFields(JsonNode node, String... expected) {
        var actual = new HashSet<String>();
        node.fieldNames().forEachRemaining(actual::add);
        assertEquals(Set.of(expected), actual);
    }

    @Test
    void concurrentRegistrationCommitsBeforeTheNextRequestReads() throws Exception {
        String username = "race-" + UUID.randomUUID().toString().substring(0, 8);
        var pool = java.util.concurrent.Executors.newFixedThreadPool(6);
        var start = new java.util.concurrent.CountDownLatch(1);
        try {
            var futures = new ArrayList<java.util.concurrent.Future<Boolean>>();
            for (int i = 0; i < 6; i++)
                futures.add(
                        pool.submit(
                                () -> {
                                    start.await();
                                    try {
                                        authService.register(new Login(username, "password-123"));
                                        return true;
                                    } catch (IllegalArgumentException expected) {
                                        return false;
                                    }
                                }));
            start.countDown();
            int successes = 0;
            for (var future : futures)
                if (future.get(10, java.util.concurrent.TimeUnit.SECONDS)) successes++;
            assertEquals(1, successes);
            assertEquals(
                    username,
                    authService.login(new Login(username, "password-123")).user().username());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void pageServesEveryReferencedScriptAndStylesheet() throws Exception {
        var http = HttpClient.newHttpClient();
        String origin = "http://127.0.0.1:" + port;
        var page =
                http.send(
                        HttpRequest.newBuilder(URI.create(origin + "/")).build(),
                        HttpResponse.BodyHandlers.ofString());
        assertEquals(200, page.statusCode());
        assertTrue(page.body().contains("/js/admin/app.js"));
        var assets =
                java.util.regex.Pattern.compile("(?:src|href)=\"([^\"]+\\.(?:js|css))\"")
                        .matcher(page.body());
        int count = 0;
        while (assets.find()) {
            String path = assets.group(1);
            var response =
                    http.send(
                            HttpRequest.newBuilder(URI.create(origin + path)).build(),
                            HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode(), path);
            assertFalse(response.body().isBlank(), path);
            assertTrue(
                    response.headers()
                            .firstValue("Content-Type")
                            .orElse("")
                            .contains(path.endsWith(".css") ? "text/css" : "javascript"),
                    path);
            count++;
        }
        assertTrue(count > 0);
    }

    @Test
    void adminDeletionRevokesTicketsAndPreventsOfflinePeersFromRestoringTheFile() throws Exception {
        String username = user("delete");
        String token = token(authService.login(new Login(username, "password-123")));
        adminService.moderate(admin, "accounts", username, "APPROVED");
        String peerId =
                peerService
                        .registerPeer(
                                token, new RegisterPeer("", "Deletion test", "127.0.0.1", 19003))
                        .id();
        adminService.moderate(admin, "peers", peerId, "APPROVED");
        byte[] bytes =
                UUID.randomUUID().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String id = Manifest.hash(bytes);
        var manifest =
                new Manifest(id, "delete.bin", bytes.length, Manifest.CHUNK_SIZE, List.of(id));
        fileService.advertise(token, new Advertisement(peerId, manifest, List.of(0)));
        approveFile(id);
        String ticket = ticketService.ticket(token, new TicketRequest(id, peerId)).ticket();
        transferService.report(token, new Progress("delete-job", peerId, id, "DOWNLOADING", 0));
        assertThrows(ResponseStatusException.class, () -> adminService.deleteFile(token, id));
        var http =
                HttpClient.newBuilder()
                        .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                        .build();
        assertEquals(401, send(http, "DELETE", "/admin/files/" + id, null).statusCode());
        assertEquals(
                200,
                send(http, "POST", "/auth/login", new Login("admin", "test-admin-secret-2026"))
                        .statusCode());
        assertEquals(200, send(http, "DELETE", "/admin/files/" + id, null).statusCode());
        assertEquals(200, send(http, "DELETE", "/admin/files/" + id, null).statusCode());
        assertTrue(fileService.deletedFileIds(token).contains(id));
        assertTrue(
                adminService.dashboard(admin).files().stream()
                        .noneMatch(f -> f.manifest().id().equals(id)));
        assertTrue(
                adminService.dashboard(admin).transfers().stream()
                        .noneMatch(t -> t.fileId().equals(id)));
        assertThrows(
                ResponseStatusException.class,
                () -> ticketService.authorize(token, new CheckTicket(ticket, peerId, id)));
        var gone =
                assertThrows(
                        ResponseStatusException.class,
                        () ->
                                fileService.advertise(
                                        token, new Advertisement(peerId, manifest, List.of(0))));
        assertEquals(410, gone.getStatusCode().value());
        adminService.moderate(admin, "accounts", username, "BLOCKED");
        assertTrue(
                fileService.deletedFileIds(token).contains(id),
                "An existing blocked session must still receive cleanup commands");
    }
}
