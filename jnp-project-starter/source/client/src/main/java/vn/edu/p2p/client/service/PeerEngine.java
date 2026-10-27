package vn.edu.p2p.client.service;

import vn.edu.p2p.client.repository.PeerStore;
import vn.edu.p2p.client.network.TrackerGateway;
import com.fasterxml.jackson.databind.*;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.p2p.client.model.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Spring lifecycle and public facade for the peer services. */
@Service
public class PeerEngine {
    private final PeerContext context;
    public PeerEngine(PeerStore store, TrackerGateway tracker, ObjectMapper json,
            @Value("${peer.tcp-port}") int tcpPort, @Value("${peer.upload-delay-ms}") int delay) throws IOException {
        context = new PeerContext(store, tracker, json, tcpPort, delay);
    }
    public Object register(Object input) throws Exception { return context.peerSessionService.register(input); }
    public boolean loggedIn() { return context.peerSessionService.loggedIn(); }
    public synchronized Object login(String username, String password, String name, String host) throws Exception { return context.peerSessionService.login(username, password, name, host); }
    public void logout() { context.peerSessionService.logout(); }
    public Object catalog(String query) throws Exception { return context.peerSessionService.catalog(query); }
    public Object sources(String id) throws Exception { return context.peerSessionService.sources(id); }
    public Object upload(MultipartFile upload) throws Exception { return context.peerLibraryService.upload(upload); }
    public void sharing(String fileId, boolean enabled) throws Exception { context.peerLibraryService.sharing(fileId, enabled); }
    public Object download(String fileId) throws Exception { return context.peerDownloadService.download(fileId); }
    public void resume(String jobId) { context.peerDownloadService.resume(jobId); }
    public void pause(String jobId) { context.peerDownloadService.pause(jobId); }
    public void simulateFault(String mode, int delayMs, int seconds) { context.peerLabService.simulateFault(mode, delayMs, seconds); }
    public Object startBenchmark(String fileId) throws Exception { return context.peerLabService.startBenchmark(fileId); }
    public Map<String, Object> status() { return context.peerStatusService.status(); }
    public Map<Integer, ChunkSource> chunkHistory(String jobId) { return context.peerDownloadService.chunkHistory(jobId); }
    public LocalFile localFile(String id) { return context.peerLibraryService.localFile(id); }
    @PreDestroy public void stop() throws IOException {
        context.closing = true;
        context.tcpServer.close(); context.tcpClient.close(); context.pulse.shutdownNow(); context.coordinators.shutdownNow();
        context.labWorker.shutdownNow(); context.telemetry.shutdownNow();
        context.downloadPools.values().forEach(ExecutorService::shutdownNow);
        synchronized (context.store) { context.store.save(); }
    }
}
