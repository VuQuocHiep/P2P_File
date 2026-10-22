package vn.edu.p2p.server;

import vn.edu.p2p.server.service.TrackerService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.p2p.common.model.Manifest;
import vn.edu.p2p.server.dto.TrackerRequests.*;
import java.net.*;
import java.net.http.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT, properties={
        "spring.datasource.url=jdbc:h2:mem:tracker-tests;DB_CLOSE_DELAY=-1", "peermesh.admin.username=admin", "peermesh.admin.password=test-admin-secret-2026"})
class TrackerIntegrationTest {
    @Autowired TrackerService service;
    @LocalServerPort int port;
    String admin;
    @BeforeEach void login() { admin = token(service.login(new Login("admin", "test-admin-secret-2026"))); }
    String token(Map<String,Object> login) { return login.get("token").toString(); }
    String user(String prefix) {
        String username = prefix + UUID.randomUUID().toString().substring(0,8);
        service.register(new Login(username, "password-123")); return username;
    }
    @Test void approvalsTicketsRevocationAndSourceOwnership() throws Exception {
        String alice = user("alice"), bob = user("bob");
        String a = token(service.login(new Login(alice,"password-123"))), b = token(service.login(new Login(bob,"password-123")));
        assertThrows(ResponseStatusException.class, () -> service.files(a, ""));
        assertThrows(ResponseStatusException.class, () -> service.dashboard(a));
        service.moderate(admin,"accounts",alice,"APPROVED"); service.moderate(admin,"accounts",bob,"APPROVED");
        String peer = service.registerPeer(a,new RegisterPeer("","Laptop","127.0.0.1",19001)).get("id").toString();
        byte[] bytes = "content for verified transfer".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String hash = Manifest.hash(bytes); var m = new Manifest(hash,"test.bin",bytes.length,Manifest.CHUNK_SIZE,List.of(hash));
        assertThrows(ResponseStatusException.class, () -> service.advertise(a,new Advertisement(peer,m,List.of(0))));
        service.moderate(admin,"peers",peer,"APPROVED"); service.advertise(a,new Advertisement(peer,m,List.of(0)));
        assertTrue(service.files(b, "test.bin").isEmpty()); service.moderate(admin,"files",hash,"APPROVED");
        assertEquals(1,service.sources(b,hash).size());
        String ticket = service.ticket(b,new TicketRequest(hash,peer)).get("ticket").toString();
        assertEquals(true,service.authorize(a,new CheckTicket(ticket,peer,hash)).get("allowed"));
        assertThrows(ResponseStatusException.class, () -> service.authorize(b,new CheckTicket(ticket,peer,hash)));
        service.moderate(admin,"accounts",bob,"BLOCKED");
        assertThrows(ResponseStatusException.class, () -> service.authorize(a,new CheckTicket(ticket,peer,hash)));
        service.moderate(admin,"accounts",bob,"APPROVED"); service.moderate(admin,"files",hash,"BLOCKED");
        assertThrows(ResponseStatusException.class, () -> service.authorize(a,new CheckTicket(ticket,peer,hash)));
        service.moderate(admin,"files",hash,"APPROVED"); service.unshare(a,peer,hash);
        assertTrue(service.sources(b,hash).isEmpty());
        assertThrows(ResponseStatusException.class, () -> service.authorize(a,new CheckTicket(ticket,peer,hash)));
    }
    @Test void browserMutationsNeedCustomHeaderAndSession() throws Exception {
        var http = HttpClient.newHttpClient();
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/auth/login"))
                .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString("{}"));
        assertEquals(403,http.send(request.build(),HttpResponse.BodyHandlers.ofString()).statusCode());
        var dashboard = HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/admin/dashboard")).build();
        assertEquals(401,http.send(dashboard,HttpResponse.BodyHandlers.ofString()).statusCode());
    }
}
