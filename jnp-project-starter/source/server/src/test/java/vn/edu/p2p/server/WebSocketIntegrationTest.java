package vn.edu.p2p.server;

import com.fasterxml.jackson.databind.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import vn.edu.p2p.server.service.TrackerService;
import vn.edu.p2p.server.dto.TrackerRequests.Login;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={
        "spring.datasource.url=jdbc:h2:mem:websocket-tests;DB_CLOSE_DELAY=-1",
        "peermesh.admin.username=admin", "peermesh.admin.password=test-admin-secret-2026"})
class WebSocketIntegrationTest {
    @Autowired TrackerService service;
    @Autowired ObjectMapper json;
    @LocalServerPort int port;

    private final class Frames implements WebSocket.Listener {
        final BlockingQueue<String> text = new LinkedBlockingQueue<>();
        final CompletableFuture<Integer> closed = new CompletableFuture<>();
        final StringBuilder partial = new StringBuilder();
        @Override public void onOpen(WebSocket socket) { socket.request(1); }
        @Override public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
            partial.append(data);
            if (last) { text.add(partial.toString()); partial.setLength(0); }
            socket.request(1); return null;
        }
        @Override public CompletionStage<?> onPing(WebSocket socket, java.nio.ByteBuffer payload) {
            socket.request(1); return socket.sendPong(payload);
        }
        @Override public CompletionStage<?> onClose(WebSocket socket, int status, String reason) {
            closed.complete(status); return null;
        }
        JsonNode next() throws Exception {
            String frame = text.poll(5, TimeUnit.SECONDS);
            assertNotNull(frame, "Expected a pushed snapshot without HTTP polling");
            return json.readTree(frame);
        }
    }

    private HttpClient client() {
        return HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
    }
    private void login(HttpClient client) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/auth/login"))
                .header("X-PeerMesh", "1").header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"username\":\"admin\",\"password\":\"test-admin-secret-2026\"}"));
        assertEquals(200,client.send(request.build(),HttpResponse.BodyHandlers.ofString()).statusCode());
    }
    private URI socketUri() { return URI.create("ws://127.0.0.1:"+port+"/ws/admin"); }

    @Test void deniesAnonymousAndForeignOriginHandshakes() throws Exception {
        var anonymous = client();
        var error = assertThrows(CompletionException.class, () -> anonymous.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(5)).buildAsync(socketUri(), new Frames()).join());
        assertEquals(401,((WebSocketHandshakeException)error.getCause()).getResponse().statusCode());
        var authenticated = client(); login(authenticated);
        error = assertThrows(CompletionException.class, () -> authenticated.newWebSocketBuilder()
                .header("Origin", "https://foreign.example").buildAsync(socketUri(), new Frames()).join());
        assertEquals(403,((WebSocketHandshakeException)error.getCause()).getResponse().statusCode());
    }

    @Test void pushesRegistryChangesAndClosesSocketAfterLogout() throws Exception {
        var authenticated = client(); login(authenticated);
        var listener = new Frames();
        WebSocket socket = authenticated.newWebSocketBuilder().buildAsync(socketUri(), listener).get(5, TimeUnit.SECONDS);
        try {
            assertEquals("snapshot", listener.next().path("type").asText());
            String username = "ws-" + UUID.randomUUID().toString().substring(0,8);
            service.register(new Login(username, "password-123"));
            boolean found = false;
            for (int i=0; i<5 && !found; i++)
                for (JsonNode account : listener.next().path("data").path("accounts"))
                    if (username.equals(account.path("username").asText())) found=true;
            assertTrue(found, "New account must arrive via the open WebSocket");
            socket.sendText("{\"type\":\"refresh\"}",true).join();
            assertEquals("snapshot",listener.next().path("type").asText());
            var logout = HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/auth/logout"))
                    .header("X-PeerMesh","1").POST(HttpRequest.BodyPublishers.noBody()).build();
            assertEquals(200, authenticated.send(logout,HttpResponse.BodyHandlers.ofString()).statusCode());
            assertEquals(1008,listener.closed.get(5,TimeUnit.SECONDS));
        } finally { socket.abort(); }
    }
}
