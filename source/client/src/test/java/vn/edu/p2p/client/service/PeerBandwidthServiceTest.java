package vn.edu.p2p.client.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import vn.edu.p2p.client.dto.request.Bandwidth;
import vn.edu.p2p.client.network.TrackerGateway;
import vn.edu.p2p.client.repository.*;
import vn.edu.p2p.client.storage.PeerStorage;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class PeerBandwidthServiceTest {
    @TempDir Path temp;
    PeerContext context;
    PeerBandwidthService service;
    final ObjectMapper json = new ObjectMapper();

    @BeforeEach void setup() throws Exception {
        open();
    }

    void open() throws Exception {
        var storage = new PeerStorage(temp.toString(), json);
        context = new PeerContext(storage, new PeerProfileRepository(storage), new LibraryRepository(storage),
                new DownloadJobRepository(storage), new PeerEventRepository(storage), mock(TrackerGateway.class), json, 0, 0);
        service = new PeerBandwidthService(context);
    }

    @AfterEach void close() throws Exception {
        context.uploadBandwidth.close();
        context.tcpClient.close();
        context.coordinators.shutdownNow();
        context.pulse.shutdownNow();
        context.telemetry.shutdownNow();
        context.labWorker.shutdownNow();
        context.storage.close();
    }

    @Test void defaultsToUnlimitedAndRestoresSavedLimitsBeforeStartingTransfers() throws Exception {
        assertEquals(0, service.settings().uploadLimitKiB());
        assertEquals(0, service.settings().downloadLimitKiB());
        service.update(new Bandwidth(64L, 32L));
        close();
        open();
        assertEquals(64, service.settings().uploadLimitKiB());
        assertEquals(32, service.settings().downloadLimitKiB());
        assertEquals(4096, context.uploadBandwidth.acquire(8192, () -> false));
        assertEquals(2048, context.downloadBandwidth.acquire(8192, () -> false));
    }

    @Test void oldProfilesWithoutBandwidthFieldsRemainUnlimited() throws Exception {
        close();
        var legacy = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(temp.resolve("profile.json").toFile());
        legacy.remove("uploadLimitKiB");
        legacy.remove("downloadLimitKiB");
        json.writeValue(temp.resolve("profile.json").toFile(), legacy);
        open();
        assertEquals(0, service.settings().uploadLimitKiB());
        assertEquals(0, service.settings().downloadLimitKiB());
        assertEquals(8192, context.uploadBandwidth.acquire(8192, () -> false));
    }

    @Test void invalidInputCannotPartiallyChangeTheSettings() throws Exception {
        service.update(new Bandwidth(64L, 32L));
        String saved = Files.readString(temp.resolve("profile.json"));
        for (var invalid : new Bandwidth[] {new Bandwidth(-1L, 1L), new Bandwidth(1L, -1L),
                new Bandwidth(1L, 1_048_577L), new Bandwidth(null, 0L), new Bandwidth(0L, null)}) {
            assertThrows(IllegalArgumentException.class, () -> service.update(invalid));
        }
        assertEquals(64, service.settings().uploadLimitKiB());
        assertEquals(32, service.settings().downloadLimitKiB());
        assertEquals(saved, Files.readString(temp.resolve("profile.json")));
    }

    @Test void persistenceFailureKeepsThePreviousProfileAndLiveLimits() throws Exception {
        Path blocked = temp.resolve("profile.json.tmp");
        Files.createDirectory(blocked);
        try {
            assertThrows(IllegalStateException.class, () -> service.update(new Bandwidth(64L, 32L)));
            assertEquals(0, service.settings().uploadLimitKiB());
            assertEquals(0, service.settings().downloadLimitKiB());
            assertEquals(8192, context.uploadBandwidth.acquire(8192, () -> false));
            assertEquals(0, json.readTree(temp.resolve("profile.json").toFile()).path("uploadLimitKiB").asLong());
        } finally {
            Files.delete(blocked);
        }
    }
}
