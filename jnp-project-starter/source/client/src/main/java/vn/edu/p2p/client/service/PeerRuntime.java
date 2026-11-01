package vn.edu.p2p.client.service;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;
import vn.edu.p2p.client.network.TcpChunkServer;

/** Owns TCP sockets, scheduled work and shutdown for the shared peer runtime. */
@Component
public class PeerRuntime {
    private final PeerContext context;

    public PeerRuntime(
            PeerContext context,
            PeerSessionService sessionService,
            PeerCatalogService catalogService,
            PeerLibraryService libraryService,
            PeerDownloadService downloadService,
            PeerSourceService sourceService,
            PeerLabService labService,
            PeerStatusService statusService,
            PeerDeletionService deletionService,
            PeerMalwareService malwareService)
            throws IOException {
        this.context = context;
        context.peerSessionService = sessionService;
        context.peerCatalogService = catalogService;
        context.peerLibraryService = libraryService;
        context.peerDownloadService = downloadService;
        context.peerSourceService = sourceService;
        context.peerLabService = labService;
        context.peerStatusService = statusService;
        context.peerDeletionService = deletionService;
        context.peerMalwareService = malwareService;
        context.tcpServer =
                new TcpChunkServer(context.tcpPort, context.delay, libraryService::readChunk);
        context.pulse.scheduleWithFixedDelay(sessionService::heartbeat, 2, 5, TimeUnit.SECONDS);
        context.telemetry.scheduleWithFixedDelay(labService::sampleNetwork, 1, 1, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void stop() throws IOException {
        context.closing = true;
        context.tcpServer.close();
        context.tcpClient.close();
        context.pulse.shutdownNow();
        context.coordinators.shutdownNow();
        context.labWorker.shutdownNow();
        context.telemetry.shutdownNow();
        context.downloadPools.values().forEach(ExecutorService::shutdownNow);
        synchronized (context.storage.monitor()) {
            context.storage.saveCheckpoint();
        }
    }
}
