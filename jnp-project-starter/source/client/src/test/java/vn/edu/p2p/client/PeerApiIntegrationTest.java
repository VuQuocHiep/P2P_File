package vn.edu.p2p.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import vn.edu.p2p.client.service.PeerStatusService;
import vn.edu.p2p.client.network.TcpChunkServer;
import vn.edu.p2p.common.model.Manifest;

import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "peer.tcp-port=0")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PeerApiIntegrationTest {
    private static HttpServer tracker;
    private static TcpChunkServer origin;
    private static final AtomicBoolean approved = new AtomicBoolean();
    private static final AtomicBoolean sourceOnline = new AtomicBoolean(true);
    private static final AtomicBoolean fileDeleted = new AtomicBoolean();
    @Autowired vn.edu.p2p.client.storage.PeerStorage storage;
    private static final byte[] content =
            "verified download after service refactor".getBytes(StandardCharsets.UTF_8);
    private static final String fileId = Manifest.hash(content);
    @LocalServerPort int port;
    @Autowired ObjectMapper json;
    @Autowired PeerStatusService statusService;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws Exception {
        origin = new TcpChunkServer(0, 0, request -> content);
        var mapper = new ObjectMapper();
        var manifest =
                new Manifest(
                        fileId,
                        "download.bin",
                        content.length,
                        Manifest.CHUNK_SIZE,
                        List.of(fileId));
        String files =
                mapper.writeValueAsString(
                        List.of(
                                Map.of(
                                        "manifest",
                                        manifest,
                                        "status",
                                        "APPROVED",
                                        "submittedBy",
                                        "bob",
                                        "sources",
                                        1,
                                        "holders",
                                        1)));
        String sources =
                mapper.writeValueAsString(
                        List.of(
                                Map.of(
                                        "id",
                                        "remote-peer",
                                        "owner",
                                        "bob",
                                        "name",
                                        "Source",
                                        "host",
                                        "127.0.0.1",
                                        "port",
                                        origin.port(),
                                        "status",
                                        "APPROVED",
                                        "online",
                                        true,
                                        "lastSeen",
                                        1,
                                        "chunks",
                                        List.of(0))));
        tracker = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        tracker.createContext(
                "/api/",
                exchange -> {
                    String user =
                            "{\"username\":\"alice\",\"role\":\"USER\",\"status\":\""
                                    + (approved.get() ? "APPROVED" : "PENDING")
                                    + "\",\"created\":1}";
                    String body =
                            switch (exchange.getRequestURI().getPath()) {
                                case "/api/auth/register" ->
                                        "{\"message\":\"registered\",\"status\":\"PENDING\"}";
                                case "/api/auth/login" ->
                                        "{\"token\":\"test-token\",\"user\":" + user + "}";
                                case "/api/auth/me" -> user;
                                case "/api/files/deletions" ->
                                        fileDeleted.get() ? "[\"" + fileId + "\"]" : "[]";
                                case "/api/peers", "/api/peers/heartbeat" ->
                                        "{\"id\":\"local-peer\",\"status\":\"APPROVED\"}";
                                case "/api/tickets" ->
                                        "{\"ticket\":\"test-ticket\",\"expiresIn\":60}";
                                case "/api/files" -> approved.get() ? files : "[]";
                                default ->
                                        exchange.getRequestURI().getPath().endsWith("/sources")
                                                ? (approved.get() && sourceOnline.get()
                                                        ? sources
                                                        : "[]")
                                                : "{\"message\":\"logged out\"}";
                            };
                    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                    exchange.getRequestBody().close();
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(200, bytes.length);
                    try (var out = exchange.getResponseBody()) {
                        out.write(bytes);
                    }
                });
        tracker.start();
        String data = Files.createTempDirectory("peer-api-test-").toString();
        registry.add("peer.data", () -> data);
        registry.add("peer.tracker", () -> "http://127.0.0.1:" + tracker.getAddress().getPort());
    }

    @AfterAll
    static void stopTracker() throws Exception {
        if (tracker != null) tracker.stop(0);
        if (origin != null) origin.close();
    }

    @Test
    void springServicesKeepPeerStatusSessionAndProxyResponses() throws Exception {
        var http =
                HttpClient.newBuilder()
                        .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                        .build();
        assertEquals(
                json.readTree("{\"authenticated\":false}"),
                send(http, "GET", "/status", null, 200));
        assertEquals(
                "PeerMesh Peer", send(http, "GET", "/health", null, 200).path("service").asText());
        send(http, "GET", "/catalog", null, 401);
        var registration =
                send(
                        http,
                        "POST",
                        "/auth/register",
                        "{\"username\":\"alice\",\"password\":\"password-123\"}",
                        200);
        assertFields(registration, "message", "status");
        send(http, "POST", "/auth/login", "{}", 400);
        var login =
                send(
                        http,
                        "POST",
                        "/auth/login",
                        "{\"username\":\"alice\",\"password\":\"password-123\",\"name\":\"Laptop\",\"host\":\"127.0.0.1\"}",
                        200);
        assertFields(
                login,
                "user",
                "tracker",
                "connection",
                "peerId",
                "peerStatus",
                "name",
                "host",
                "tcpPort",
                "uploaded",
                "catalog",
                "lab",
                "network",
                "library",
                "jobs",
                "events");
        assertEquals("alice", login.path("user").path("username").asText());
        assertEquals("Laptop", login.path("name").asText());
        assertFields(
                login.path("lab"),
                "fault",
                "benchmark",
                "samples",
                "links",
                "recentLinks",
                "sourceStats");
        assertFields(
                login.path("network"),
                "upload",
                "download",
                "downloadWorkers",
                "activeDownloads",
                "workerLimitPerFile",
                "fileLimit");
        assertEquals(4, login.path("network").path("workerLimitPerFile").asInt());
        assertEquals(2, login.path("network").path("fileLimit").asInt());
        assertEquals("Laptop", send(http, "GET", "/status", null, 200).path("name").asText());
        assertEquals(json.createArrayNode(), send(http, "GET", "/catalog?q=test", null, 200));
        assertEquals(
                json.createArrayNode(),
                send(http, "GET", "/files/" + "a".repeat(64) + "/sources", null, 200));
        send(http, "POST", "/auth/logout", null, 200);
        assertEquals(
                json.readTree("{\"authenticated\":false}"),
                send(http, "GET", "/status", null, 200));
        assertNull(statusService.status().user());

        // Exercise the actual download workers, TCP transfer, checkpoint and local content
        // endpoint.
        approved.set(true);
        send(
                http,
                "POST",
                "/auth/login",
                "{\"username\":\"alice\",\"password\":\"password-123\",\"name\":\"Laptop\",\"host\":\"127.0.0.1\"}",
                200);
        var catalog = send(http, "GET", "/catalog", null, 200);
        assertEquals(fileId, catalog.get(0).path("manifest").path("id").asText());
        var download = send(http, "POST", "/downloads", "{\"fileId\":\"" + fileId + "\"}", 200);
        assertFields(download, "id");
        JsonNode job = null;
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            var status = send(http, "GET", "/status", null, 200);
            if (!status.path("jobs").isEmpty()) {
                job = status.path("jobs").get(0);
                if ("COMPLETED".equals(job.path("status").asText())) break;
            }
            Thread.sleep(50);
        }
        assertNotNull(job);
        assertEquals("COMPLETED", job.path("status").asText(), job.toString());
        assertFields(
                job,
                "id",
                "fileId",
                "name",
                "status",
                "bytes",
                "size",
                "speed",
                "error",
                "sourceBytes",
                "chunks");
        assertEquals(content.length, job.path("bytes").asLong());
        var history =
                send(
                        http,
                        "GET",
                        "/downloads/" + download.path("id").asText() + "/chunks",
                        null,
                        200);
        assertEquals("remote-peer", history.path("0").path("peerId").asText());
        origin.close();
        origin = null;
        sourceOnline.set(false);
        assertEquals(
                json.createArrayNode(),
                send(http, "GET", "/files/" + fileId + "/sources", null, 200));
        var offlineHistory =
                send(
                        http,
                        "GET",
                        "/downloads/" + download.path("id").asText() + "/chunks",
                        null,
                        200);
        assertEquals(
                history,
                offlineHistory,
                "Saved chunk history must not depend on the source staying online");
        var request =
                HttpRequest.newBuilder(
                                URI.create(
                                        "http://127.0.0.1:"
                                                + port
                                                + "/api/library/"
                                                + fileId
                                                + "/content"))
                        .build();
        var downloaded = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, downloaded.statusCode());
        assertArrayEquals(content, downloaded.body());
        fileDeleted.set(true);
        long deletionDeadline =
                System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(12);
        JsonNode afterDeletion = null;
        while (System.nanoTime() < deletionDeadline) {
            afterDeletion = send(http, "GET", "/status", null, 200);
            if (afterDeletion.path("library").isEmpty() && afterDeletion.path("jobs").isEmpty())
                break;
            Thread.sleep(100);
        }
        assertNotNull(afterDeletion);
        assertTrue(afterDeletion.path("library").isEmpty(), afterDeletion.toString());
        assertTrue(afterDeletion.path("jobs").isEmpty(), afterDeletion.toString());
        assertFalse(Files.exists(storage.blob(fileId)));
        assertFalse(Files.exists(storage.chunk(fileId, 0).getParent()));
        assertTrue(storage.isDeleted(fileId));
        send(http, "POST", "/downloads", "{\"fileId\":\"" + fileId + "\"}", 400);
        send(http, "POST", "/auth/logout", null, 200);
    }

    private JsonNode send(
            HttpClient http, String method, String path, String body, int expectedStatus)
            throws Exception {
        var request =
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api" + path))
                        .header("X-PeerMesh", "1")
                        .header("Content-Type", "application/json")
                        .method(
                                method,
                                body == null
                                        ? HttpRequest.BodyPublishers.noBody()
                                        : HttpRequest.BodyPublishers.ofString(body));
        var response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(expectedStatus, response.statusCode(), response.body());
        return json.readTree(response.body());
    }

    private void assertFields(JsonNode node, String... expected) {
        var actual = new HashSet<String>();
        node.fieldNames().forEachRemaining(actual::add);
        assertEquals(Set.of(expected), actual);
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
        assertTrue(page.body().contains("/js/peer/app.js"));
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
}
