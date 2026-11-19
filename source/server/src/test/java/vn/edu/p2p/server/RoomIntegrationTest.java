package vn.edu.p2p.server;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;
import vn.edu.p2p.common.model.Manifest;
import vn.edu.p2p.server.dto.request.*;
import vn.edu.p2p.server.dto.response.*;
import vn.edu.p2p.server.service.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "peermesh.admin.username=admin",
            "peermesh.admin.password=test-admin-secret-2026",
            "virustotal.worker-enabled=false"
        })
class RoomIntegrationTest {
    @org.springframework.test.context.DynamicPropertySource
    static void database(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> TestDatabase.url("peermesh_test_room"));
        registry.add("spring.datasource.username", TestDatabase::username);
        registry.add("spring.datasource.password", TestDatabase::password);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }
    private static final AtomicInteger PORTS = new AtomicInteger(19100);

    @Autowired TrackerAuthService authService;
    @Autowired TrackerAdminService adminService;
    @Autowired TrackerPeerService peerService;
    @Autowired TrackerFileService fileService;
    @Autowired TrackerRoomService roomService;
    @Autowired TrackerTicketService ticketService;

    String admin;

    @BeforeEach
    void login() {
        admin = authService.login(new Login("admin", "test-admin-secret-2026")).token();
    }

    String user(String prefix) {
        String username = prefix + UUID.randomUUID().toString().substring(0, 8);
        authService.register(new Login(username, "password-123"));
        adminService.moderate(admin, "accounts", username, "APPROVED");
        return username;
    }

    String token(String username) {
        return authService.login(new Login(username, "password-123")).token();
    }

    String peer(String token) throws Exception {
        String id =
                peerService
                        .registerPeer(
                                token,
                                new RegisterPeer(
                                        "", "Room peer", "127.0.0.1", PORTS.getAndIncrement()))
                        .id();
        adminService.moderate(admin, "peers", id, "APPROVED");
        return id;
    }

    Manifest manifest(String name) {
        byte[] bytes = (name + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
        String id = Manifest.hash(bytes);
        return new Manifest(id, name, bytes.length, Manifest.CHUNK_SIZE, List.of(id));
    }

    static String roomId(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 6);
    }

    @Test
    void ownerApprovesMembersAndRoomFilesStayInsideTheRoom() throws Exception {
        String alice = user("alice"), bob = user("bob"), carol = user("carol");
        String a = token(alice), b = token(bob), c = token(carol);
        String roomId = roomId("ltm");

        var room = roomService.create(a, new RoomCreate("Nhóm LTM", roomId, "secret-1"));
        assertEquals(roomId, room.id());
        assertTrue(room.mine());
        assertTrue(room.hasPassword());
        assertEquals("OWNER", room.membership());
        assertEquals(1, room.members());

        assertEquals("PENDING", roomService.join(b, roomId, new RoomJoin("secret-1")).membership());
        assertThrows(
                IllegalArgumentException.class,
                () -> roomService.join(c, roomId, new RoomJoin("wrong-password")));
        assertThrows(
                IllegalArgumentException.class,
                () -> roomService.join(b, roomId, new RoomJoin("secret-1")));

        assertEquals(1, roomService.requests(a, roomId).size());
        assertThrows(IllegalArgumentException.class, () -> roomService.requests(b, roomId));
        assertThrows(
                IllegalArgumentException.class,
                () -> roomService.decide(b, roomId, bob, new RoomDecision("APPROVED")));

        assertEquals("APPROVED", roomService.decide(a, roomId, bob, new RoomDecision("APPROVED")).status());
        assertEquals(2, roomService.members(a, roomId).size());
        assertEquals(
                2,
                roomService.rooms(b).stream()
                        .filter(r -> r.id().equals(roomId))
                        .findFirst()
                        .orElseThrow()
                        .members());

        assertThrows(ResponseStatusException.class, () -> roomService.files(c, roomId));

        String alicePeer = peer(a);
        peer(b);
        peer(c);
        Manifest roomFile = manifest("room-file.txt");
        fileService.advertise(a, new Advertisement(alicePeer, roomFile, List.of(0), false, roomId));
        adminService.moderate(admin, "files", roomFile.id(), "APPROVED");

        assertEquals(1, roomService.files(b, roomId).size());
        assertTrue(
                fileService.files(b, "room-file").stream()
                        .anyMatch(f -> f.manifest().id().equals(roomFile.id())));
        assertTrue(fileService.files(c, "room-file").isEmpty());
        assertThrows(ResponseStatusException.class, () -> fileService.sources(c, roomFile.id()));
        assertEquals(1, fileService.sources(b, roomFile.id()).size());

        assertThrows(
                ResponseStatusException.class,
                () -> ticketService.ticket(c, new TicketRequest(roomFile.id(), alicePeer)));
        String ticket =
                ticketService
                        .ticket(b, new TicketRequest(roomFile.id(), alicePeer))
                        .ticket();
        assertTrue(
                ticketService
                        .authorize(a, new CheckTicket(ticket, alicePeer, roomFile.id()))
                        .allowed());

        String secondRoom = roomId("two");
        roomService.create(a, new RoomCreate("Nhóm phụ", secondRoom, null));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        fileService.advertise(
                                a, new Advertisement(alicePeer, roomFile, List.of(0), false, secondRoom)));

        Manifest publicFile = manifest("public.txt");
        fileService.advertise(c, new Advertisement(peer(c), publicFile, List.of(0)));
        adminService.moderate(admin, "files", publicFile.id(), "APPROVED");
        assertTrue(
                fileService.files(b, "public").stream()
                        .anyMatch(f -> f.manifest().id().equals(publicFile.id())));
        assertEquals(1, roomService.files(b, roomId).size());
        assertTrue(roomService.rooms(c).stream().anyMatch(r -> r.id().equals(secondRoom)));
        var discovered =
                roomService.rooms(c).stream()
                        .filter(r -> r.id().equals(roomId))
                        .findFirst()
                        .orElseThrow();
        assertNull(discovered.membership());
        assertEquals(0, discovered.pendingRequests());
        var adminRoom =
                adminService.dashboard(admin).rooms().stream()
                        .filter(r -> r.id().equals(roomId))
                        .findFirst()
                        .orElseThrow();
        assertEquals(alice, adminRoom.owner());
        assertEquals(2, adminRoom.members());
    }

    @Test
    void ownerManagesRoomNamePasswordMembersAndDeletion() throws Exception {
        String alice = user("alice"), bob = user("bob"), carol = user("carol");
        String a = token(alice), b = token(bob), c = token(carol);
        String roomId = roomId("mgmt");
        roomService.create(a, new RoomCreate("Phòng gốc", roomId, "secret-1"));
        assertEquals("PENDING", roomService.join(b, roomId, new RoomJoin("secret-1")).membership());
        roomService.decide(a, roomId, bob, new RoomDecision("APPROVED"));

        assertEquals("Phòng mới", roomService.update(a, roomId, new RoomUpdate("Phòng mới", null)).name());
        assertTrue(roomService.update(a, roomId, new RoomUpdate(null, "secret-2")).hasPassword());

        assertThrows(
                IllegalArgumentException.class,
                () -> roomService.join(c, roomId, new RoomJoin("secret-1")));
        assertEquals("PENDING", roomService.join(c, roomId, new RoomJoin("secret-2")).membership());
        assertFalse(roomService.update(a, roomId, new RoomUpdate(null, "")).hasPassword());

        assertThrows(
                IllegalArgumentException.class,
                () -> roomService.update(b, roomId, new RoomUpdate("Hack", null)));
        assertThrows(IllegalArgumentException.class, () -> roomService.delete(b, roomId));
        assertThrows(IllegalArgumentException.class, () -> roomService.removeMember(b, roomId, alice));

        roomService.removeMember(a, roomId, bob);
        assertEquals(
                "REJECTED",
                roomService.rooms(b).stream()
                        .filter(r -> r.id().equals(roomId))
                        .findFirst()
                        .orElseThrow()
                        .membership());
        assertThrows(ResponseStatusException.class, () -> roomService.files(b, roomId));
        assertEquals("PENDING", roomService.join(b, roomId, new RoomJoin(null)).membership());

        String alicePeer = peer(a);
        Manifest file = manifest("xoa-phong.txt");
        fileService.advertise(a, new Advertisement(alicePeer, file, List.of(0), false, roomId));
        adminService.moderate(admin, "files", file.id(), "APPROVED");
        assertEquals(1, roomService.files(a, roomId).size());

        roomService.delete(a, roomId);
        assertTrue(roomService.rooms(a).stream().noneMatch(r -> r.id().equals(roomId)));
        assertThrows(ResponseStatusException.class, () -> roomService.files(a, roomId));
        assertThrows(IllegalArgumentException.class, () -> fileService.sources(a, file.id()));
        assertTrue(fileService.files(a, "xoa-phong").isEmpty());
        assertTrue(fileService.deletedFileIds(a).contains(file.id()));
        assertThrows(ResponseStatusException.class, () -> roomService.join(b, roomId, null));
    }

    @Test
    void passwordlessRoomsSupportRejectionAndRepeatedRequests() throws Exception {
        String alice = user("ana"), bob = user("ben");
        String a = token(alice), b = token(bob);
        String roomId = roomId("open");

        var room = roomService.create(a, new RoomCreate("Phòng mở", roomId, null));
        assertFalse(room.hasPassword());
        assertEquals("PENDING", roomService.join(b, roomId, new RoomJoin(null)).membership());

        assertEquals("REJECTED", roomService.decide(a, roomId, bob, new RoomDecision("REJECTED")).status());
        assertEquals(
                "REJECTED",
                roomService.rooms(b).stream()
                        .filter(r -> r.id().equals(roomId))
                        .findFirst()
                        .orElseThrow()
                        .membership());

        assertEquals("PENDING", roomService.join(b, roomId, null).membership());
        roomService.decide(a, roomId, bob, new RoomDecision("APPROVED"));
        assertTrue(
                roomService.rooms(b).stream()
                        .anyMatch(
                                r ->
                                        r.id().equals(roomId)
                                                && "APPROVED".equals(r.membership())
                                                && !r.mine()));

        assertThrows(
                IllegalArgumentException.class,
                () -> roomService.create(b, new RoomCreate("Trùng mã", roomId, null)));
        assertThrows(
                IllegalArgumentException.class,
                () -> roomService.create(b, new RoomCreate("Sai mã", "ab!", null)));
        assertThrows(
                IllegalArgumentException.class,
                () -> roomService.create(b, new RoomCreate("Sai mật khẩu", "", "123")));

        String generated = roomService.create(b, new RoomCreate("Tự sinh", "", null)).id();
        assertTrue(generated.matches("[a-z0-9]{8}"), generated);
    }
}
