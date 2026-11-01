package vn.edu.p2p.client.service;

import vn.edu.p2p.client.repository.*;
import vn.edu.p2p.client.storage.PeerStorage;
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
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Shared peer runtime; repository operations retain the peer repository monitor. */
@Component
final class PeerContext {
    final PeerStorage storage;
    final PeerProfileRepository profile;
    final LibraryRepository library;
    final DownloadJobRepository jobs;
    final PeerEventRepository events;
    final TrackerGateway tracker;
    final ObjectMapper json;
    final int tcpPort;
    TcpChunkServer tcpServer;
    final int delay;
    final TcpChunkClient tcpClient = new TcpChunkClient();
    final ExecutorService coordinators =
            Executors.newFixedThreadPool(2, NamedThreads.factory("download-coordinator"));
    final ScheduledExecutorService pulse =
            Executors.newSingleThreadScheduledExecutor(NamedThreads.factory("peer-heartbeat"));
    final ScheduledExecutorService telemetry =
            Executors.newSingleThreadScheduledExecutor(NamedThreads.factory("peer-metrics"));
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
    final Map<String, AtomicLong> sourceReceived = new ConcurrentHashMap<>(),
            sourceChunks = new ConcurrentHashMap<>();
    final Map<String, Long> sourcePrevious = new HashMap<>();
    final Map<String, Map<String, Long>> sourceStats = new ConcurrentHashMap<>();
    final List<Map<String, Object>> samples = new ArrayList<>();
    long sampleAt = System.nanoTime(), sampleRx, sampleTx;
    final ExecutorService labWorker =
            Executors.newSingleThreadExecutor(NamedThreads.factory("demo-benchmark"));
    final AtomicBoolean benchmarking = new AtomicBoolean();
    volatile Map<String, Object> benchmark = Map.of("status", "IDLE");

    PeerSessionService peerSessionService;
    PeerCatalogService peerCatalogService;
    PeerLibraryService peerLibraryService;
    PeerDownloadService peerDownloadService;
    PeerSourceService peerSourceService;
    PeerLabService peerLabService;
    PeerStatusService peerStatusService;
    PeerDeletionService peerDeletionService;
    PeerMalwareService peerMalwareService;

    PeerContext(
            PeerStorage storage,
            PeerProfileRepository profile,
            LibraryRepository library,
            DownloadJobRepository jobs,
            PeerEventRepository events,
            TrackerGateway tracker,
            ObjectMapper json,
            @Value("${peer.tcp-port}") int tcpPort,
            @Value("${peer.upload-delay-ms}") int delay) {
        this.delay = delay;
        this.storage = storage;
        this.profile = profile;
        this.library = library;
        this.jobs = jobs;
        this.events = events;
        this.tracker = tracker;
        this.json = json;
        this.tcpPort = tcpPort;
        catalogSnapshot = json.createArrayNode();
    }
}
