package vn.edu.p2p.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class VirusTotalClientTest {
    HttpServer server;
    VirusTotalClient client;
    String hash = "a".repeat(64);
    String body;
    int status = 200;
    String requestPath, requestKey, requestBody;

    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v3/", exchange -> {
            requestPath = exchange.getRequestURI().getPath();
            requestKey = exchange.getRequestHeaders().getFirst("x-apikey");
            requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        client = newClient("test-secret-key");
    }

    VirusTotalClient newClient(String key) {
        return new VirusTotalClient(key, "http://127.0.0.1:" + server.getAddress().getPort() + "/api/v3", new ObjectMapper());
    }

    @AfterEach void stop() { server.stop(0); }

    void report(String stats, String results) {
        body = "{\"data\":{\"id\":\"" + hash + "\",\"attributes\":{\"last_analysis_date\":1700000000,\"last_analysis_stats\":"
                + stats + ",\"last_analysis_results\":" + results + "}}}";
    }

    @Test void detectsMalwareAndPreservesEvidence() throws Exception {
        report("{\"malicious\":2,\"suspicious\":1,\"undetected\":65,\"timeout\":2}",
                "{\"Engine A\":{\"category\":\"malicious\",\"result\":\"EICAR-Test\"}}");
        var result = client.lookup(hash);
        assertEquals("MALICIOUS", result.status());
        assertEquals(70, result.total());
        assertEquals(2, result.malicious());
        assertEquals("EICAR-Test", result.engines().get(0).result());
        assertEquals(1700000000000L, result.analysisAt());
        assertFalse(result.permitsApproval());
        assertEquals("/api/v3/files/" + hash, requestPath);
        assertEquals("test-secret-key", requestKey);
        assertFalse(new ObjectMapper().writeValueAsString(result).contains("test-secret-key"));
    }

    @Test void distinguishesNoDetectionsUnknownAndInconclusive() throws Exception {
        report("{\"undetected\":70}", "{}");
        assertTrue(client.lookup(hash).permitsApproval());
        report("{\"timeout\":70}", "{}");
        assertEquals("UNKNOWN", newClient("key").lookup(hash).status());
        status = 404; body = "{}";
        assertEquals("UNKNOWN", newClient("key").lookup(hash).status());
    }

    @Test void suspiciousIsNotClearAndWrongHashIsRejected() throws Exception {
        report("{\"suspicious\":1,\"undetected\":69}", "{}");
        assertEquals("SUSPICIOUS", client.lookup(hash).status());
        assertThrows(IllegalArgumentException.class, () -> newClient("key").lookup("b".repeat(64)));
    }

    @Test void handlesMissingKeyQuotaAuthenticationAndMalformedResponses() {
        assertThrows(IllegalArgumentException.class, () -> newClient("").lookup(hash));
        body = "{}";
        for (int code : new int[] {401, 403, 429, 500}) {
            status = code;
            assertThrows(IllegalArgumentException.class, () -> newClient("key").lookup(hash));
        }
        status = 200;
        assertThrows(IllegalArgumentException.class, () -> newClient("key").lookup(hash));
    }

    @Test void enforcesGlobalRequestInterval() throws Exception {
        status = 404; body = "{}";
        client.lookup(hash);
        assertThrows(IllegalArgumentException.class, () -> client.lookup(hash));
    }

    @Test void uploadsMultipartAndTracksQueuedAndCompletedJobs() throws Exception {
        body = "{\"data\":{\"id\":\"analysis-123\"}}";
        var queued = client.upload("test-content".getBytes(StandardCharsets.UTF_8));
        assertEquals("QUEUED", queued.status());
        assertEquals("analysis-123", queued.analysisId());
        assertEquals("/api/v3/files", requestPath);
        assertTrue(requestBody.contains("name=\"file\""));
        assertTrue(requestBody.contains("test-content"));
        body = "{\"data\":{\"attributes\":{\"status\":\"queued\"}}}";
        assertEquals("QUEUED", newClient("key").poll(hash, queued.analysisId()).status());
        body = "{\"data\":{\"attributes\":{\"status\":\"completed\"}}}";
        assertEquals("READY", newClient("key").poll(hash, queued.analysisId()).status());
    }
}
