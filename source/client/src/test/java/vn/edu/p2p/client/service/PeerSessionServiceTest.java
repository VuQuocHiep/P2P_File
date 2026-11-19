package vn.edu.p2p.client.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import vn.edu.p2p.client.exception.TrackerFailure;
import vn.edu.p2p.client.mapper.TrackerResponseMapper;
import vn.edu.p2p.client.network.TrackerGateway;
import vn.edu.p2p.client.repository.*;
import vn.edu.p2p.client.storage.PeerStorage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** A peer id stored on disk can outlive the tracker row it points at. */
class PeerSessionServiceTest {
    @TempDir Path temp;
    final ObjectMapper json = new ObjectMapper();
    final List<String> registeredIds = new ArrayList<>();
    final List<String> heartbeatIds = new ArrayList<>();
    PeerStorage storage;
    PeerContext context;
    TrackerGateway tracker;
    PeerSessionService service;

    @BeforeEach
    void setup() throws Exception {
        storage = new PeerStorage(temp.toString(), json);
        tracker = mock(TrackerGateway.class);
        when(tracker.token()).thenReturn("test-token");
        context =
                new PeerContext(
                        storage,
                        new PeerProfileRepository(storage),
                        new LibraryRepository(storage),
                        new DownloadJobRepository(storage),
                        new PeerEventRepository(storage),
                        tracker,
                        json,
                        0,
                        0);
        service = new PeerSessionService(context, new TrackerResponseMapper(json));
        context.peerDownloadService = mock(PeerDownloadService.class);
        context.peerDeletionService = new PeerDeletionService(context);
        saveProfile("stale-peer");
    }

    @AfterEach
    void close() throws Exception {
        context.uploadBandwidth.close();
        context.tcpClient.close();
        context.coordinators.shutdownNow();
        context.pulse.shutdownNow();
        context.telemetry.shutdownNow();
        context.labWorker.shutdownNow();
        storage.close();
    }

    private void saveProfile(String peerId) {
        synchronized (storage.monitor()) {
            context.profile.update("alice", "Laptop", "127.0.0.1");
            context.profile.updatePeerId(peerId);
            storage.saveCheckpoint();
        }
    }

    /** The tracker lost the peer row, e.g. its database was recreated. */
    @Test
    void connectPeerReplacesTheStoredIdTheTrackerRejected() throws Exception {
        when(tracker.call(anyString(), any(), any()))
                .thenAnswer(
                        invocation -> {
                            assertEquals("POST", invocation.getArgument(0));
                            assertEquals("/api/peers", invocation.getArgument(1));
                            String id = String.valueOf(((Map<?, ?>) invocation.getArgument(2)).get("id"));
                            registeredIds.add(id);
                            if (!id.isBlank())
                                throw new TrackerFailure(
                                        403, "Peer không thuộc tài khoản hoặc chưa được duyệt.");
                            return json.readTree("{\"id\":\"fresh-peer\",\"status\":\"PENDING\"}");
                        });

        service.connectPeer();

        assertEquals(List.of("stale-peer", ""), registeredIds);
        assertEquals("fresh-peer", context.profile.getPeerId());
        assertEquals("PENDING", context.peerStatus);
        assertEquals("Đã kết nối tracker", context.connection);
        assertEquals(
                "fresh-peer",
                json.readTree(temp.resolve("profile.json").toFile()).path("peerId").asText());
        assertTrue(
                context.events.findAll().stream().anyMatch(e -> e.endsWith("Đăng ký peer Laptop")));
    }

    /** The peer row disappeared while this peer was already online. */
    @Test
    void heartbeatReRegistersAPeerTheTrackerNoLongerKnows() throws Exception {
        when(tracker.call(anyString(), any(), any()))
                .thenAnswer(
                        invocation -> {
                            String path = invocation.getArgument(1);
                            Object body = invocation.getArgument(2);
                            if ("/api/auth/me".equals(path)) return user("APPROVED");
                            if ("/api/peers/heartbeat".equals(path)) {
                                String id = String.valueOf(((Map<?, ?>) body).get("peerId"));
                                heartbeatIds.add(id);
                                if (!"fresh-peer".equals(id))
                                    throw new TrackerFailure(
                                            403,
                                            "Peer không thuộc tài khoản hoặc chưa được duyệt.");
                                return json.readTree(
                                        "{\"id\":\"fresh-peer\",\"status\":\"APPROVED\"}");
                            }
                            if ("/api/peers".equals(path)) {
                                String id = String.valueOf(((Map<?, ?>) body).get("id"));
                                registeredIds.add(id);
                                return json.readTree(
                                        "{\"id\":\"fresh-peer\",\"status\":\"APPROVED\"}");
                            }
                            return json.createArrayNode();
                        });

        service.heartbeat();

        assertEquals(List.of("stale-peer", "fresh-peer"), heartbeatIds);
        assertEquals(List.of(""), registeredIds);
        assertEquals("fresh-peer", context.profile.getPeerId());
        assertEquals("APPROVED", context.peerStatus);
        assertEquals("Đã kết nối tracker", context.connection);
    }

    @Test
    void otherTrackerFailuresKeepTheStoredIdAndAreReported() throws Exception {
        when(tracker.call(anyString(), any(), any()))
                .thenThrow(new TrackerFailure(400, "Host phải là địa chỉ IP."));

        var failure = assertThrows(TrackerFailure.class, service::connectPeer);

        assertEquals(400, failure.status);
        assertEquals("Host phải là địa chỉ IP.", failure.getMessage());
        assertEquals("stale-peer", context.profile.getPeerId());
        verify(tracker, times(1)).call(anyString(), any(), any());
    }

    /** A blank id cannot be stale, so a rejection must surface instead of looping. */
    @Test
    void blankPeerIdIsNotRetriedAfterARejection() throws Exception {
        saveProfile("");
        when(tracker.call(anyString(), any(), any()))
                .thenThrow(
                        new TrackerFailure(403, "Tài khoản chưa được duyệt hoặc đã bị khóa."));

        var failure = assertThrows(TrackerFailure.class, service::connectPeer);

        assertEquals(403, failure.status);
        verify(tracker, times(1)).call(anyString(), any(), any());
    }

    private JsonNode user(String status) {
        return json.createObjectNode()
                .put("username", "alice")
                .put("role", "USER")
                .put("status", status)
                .put("created", 1);
    }
}
