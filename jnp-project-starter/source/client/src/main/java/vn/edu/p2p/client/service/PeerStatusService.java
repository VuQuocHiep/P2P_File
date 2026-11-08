package vn.edu.p2p.client.service;

import com.fasterxml.jackson.databind.*;
import vn.edu.p2p.client.model.*;
import vn.edu.p2p.client.dto.response.PeerStatusResponse.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import org.springframework.stereotype.Service;
import vn.edu.p2p.client.dto.response.*;
import vn.edu.p2p.client.mapper.LibraryMapper;
import vn.edu.p2p.client.mapper.JobMapper;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

@Service
public class PeerStatusService {
    private final PeerContext context;
    private final LibraryMapper libraryMapper;
    private final JobMapper jobMapper;
    private final PeerBandwidthService bandwidthService;

    public PeerStatusService(
            PeerContext context, LibraryMapper libraryMapper, JobMapper jobMapper,
            PeerBandwidthService bandwidthService) {
        this.context = context;
        this.libraryMapper = libraryMapper;
        this.jobMapper = jobMapper;
        this.bandwidthService = bandwidthService;
    }

    public PeerStatusResponse status() {
        synchronized (context.storage.monitor()) {
            var user = context.user;
            String tracker = context.tracker.base(), connection = context.connection;
            String peerId = context.profile.getPeerId(), peerStatus = context.peerStatus;
            String name = context.profile.getName(), host = context.profile.getHost();
            long uploaded = context.tcpServer.sentBytes();
            var catalog = context.catalogSnapshot;
            var lab =
                    new LabResponse(
                            context.tcpServer.fault(),
                            context.benchmark,
                            context.peerLabService.networkSamples(),
                            new ArrayList<>(context.links.values()),
                            new ArrayList<>(context.recentLinks.values()),
                            new TreeMap<>(context.sourceStats));
            var network =
                    new NetworkResponse(
                            context.tcpServer.metrics(),
                            context.tcpClient.metrics(),
                            context.downloadPools.values().stream()
                                    .mapToInt(ThreadPoolExecutor::getActiveCount)
                                    .sum(),
                            context.running.size(),
                            4,
                            2,
                            bandwidthService.settings());
            var library = new ArrayList<LibraryResponse>();
            for (Entry e : context.library.findAll()) library.add(libraryMapper.toResponse(e));
            var jobs = new ArrayList<JobResponse>();
            for (Job j : context.jobs.findAll()) {
                Entry e = context.library.findByFileId(j.fileId);
                long elapsed =
                        Math.max(
                                1,
                                System.currentTimeMillis()
                                        - context.speedStart.getOrDefault(
                                                j.id, System.currentTimeMillis()));
                long speed =
                        context.peerDownloadService.active(j.id)
                                ? context.speedBytes.getOrDefault(j.id, new AtomicLong()).get()
                                        * 1000
                                        / elapsed
                                : 0;
                jobs.add(
                        jobMapper.toResponse(
                                j, e, context.peerDownloadService.completedBytes(e), speed));
            }
            return new PeerStatusResponse(
                    user,
                    tracker,
                    connection,
                    peerId,
                    peerStatus,
                    name,
                    host,
                    context.tcpPort,
                    uploaded,
                    catalog,
                    lab,
                    network,
                    library,
                    jobs,
                    new ArrayList<>(context.events.findAll()));
        }
    }
}
