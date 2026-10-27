package vn.edu.p2p.client.service;

import vn.edu.p2p.client.repository.PeerStore;
import vn.edu.p2p.client.network.TrackerGateway;
import vn.edu.p2p.client.network.TcpChunkServer;
import vn.edu.p2p.client.network.TcpChunkClient;
import vn.edu.p2p.common.concurrent.NamedThreads;
import com.fasterxml.jackson.databind.*;
import vn.edu.p2p.client.model.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Shared peer runtime; repository operations retain the PeerStore monitor. */
final class PeerContext {
    final PeerStore store;
    final TrackerGateway tracker;
    final ObjectMapper json;
    final int tcpPort;
    final TcpChunkServer tcpServer;
    final TcpChunkClient tcpClient = new TcpChunkClient();
    final ExecutorService coordinators = Executors.newFixedThreadPool(2, NamedThreads.factory("download-coordinator"));
    final ScheduledExecutorService pulse = Executors.newSingleThreadScheduledExecutor(NamedThreads.factory("peer-heartbeat"));
    final ScheduledExecutorService telemetry = Executors.newSingleThreadScheduledExecutor(NamedThreads.factory("peer-metrics"));
    final Set<String> running = ConcurrentHashMap.newKeySet();
    final Map<String, ThreadPoolExecutor> downloadPools = new ConcurrentHashMap<>();
    volatile JsonNode catalogSnapshot;
    volatile JsonNode user;
    volatile String connection = "Chưa đăng nhập", peerStatus = "PENDING";
    volatile boolean closing;
    final Map<String, AtomicLong> speedBytes = new ConcurrentHashMap<>();
    final Map<String, Long> speedStart = new ConcurrentHashMap<>();
    final Map<String, Map<String, Object>> links = new ConcurrentHashMap<>();
    final Map<String, Map<String, Object>> recentLinks = new ConcurrentHashMap<>();
    final Map<String, AtomicLong> sourceReceived = new ConcurrentHashMap<>(), sourceChunks = new ConcurrentHashMap<>();
    final Map<String, Long> sourcePrevious = new HashMap<>();
    final Map<String, Map<String, Long>> sourceStats = new ConcurrentHashMap<>();
    final List<Map<String, Object>> samples = new ArrayList<>();
    long sampleAt = System.nanoTime(), sampleRx, sampleTx;
    final ExecutorService labWorker = Executors.newSingleThreadExecutor(NamedThreads.factory("demo-benchmark"));
    final AtomicBoolean benchmarking = new AtomicBoolean();
    volatile Map<String, Object> benchmark = Map.of("status", "IDLE");

    final PeerSessionService peerSessionService;
    final PeerLibraryService peerLibraryService;
    final PeerDownloadService peerDownloadService;
    final PeerSourceService peerSourceService;
    final PeerLabService peerLabService;
    final PeerStatusService peerStatusService;
    PeerContext(PeerStore store, TrackerGateway tracker, ObjectMapper json,
            int tcpPort, int delay) throws IOException {
        this.store = store; this.tracker = tracker; this.json = json; this.tcpPort = tcpPort;
        catalogSnapshot = json.createArrayNode();
        peerSessionService = new PeerSessionService(this);
        peerLibraryService = new PeerLibraryService(this);
        peerDownloadService = new PeerDownloadService(this);
        peerSourceService = new PeerSourceService(this);
        peerLabService = new PeerLabService(this);
        peerStatusService = new PeerStatusService(this);
        tcpServer = new TcpChunkServer(tcpPort, delay, peerLibraryService::readChunk);
        pulse.scheduleWithFixedDelay(peerSessionService::heartbeat, 2, 5, TimeUnit.SECONDS);
        telemetry.scheduleWithFixedDelay(peerLabService::sampleNetwork, 1, 1, TimeUnit.SECONDS);
    }
}
