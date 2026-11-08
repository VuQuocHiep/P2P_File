package vn.edu.p2p.client.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import vn.edu.p2p.common.model.Manifest;
import vn.edu.p2p.client.model.FaultState;
import java.util.List;
import java.util.Map;

public record PeerStatusResponse(
        JsonNode user,
        String tracker,
        String connection,
        String peerId,
        String peerStatus,
        String name,
        String host,
        int tcpPort,
        long uploaded,
        JsonNode catalog,
        LabResponse lab,
        NetworkResponse network,
        List<LibraryResponse> library,
        List<JobResponse> jobs,
        List<String> events)
        implements PeerStatusView {
    public record LibraryResponse(
            Manifest manifest,
            boolean complete,
            boolean sharing,
            String catalogStatus,
            List<Integer> chunks,
            boolean scanConsent) {}

    public record JobResponse(
            String id,
            String fileId,
            String name,
            String status,
            long bytes,
            long size,
            long speed,
            String error,
            Map<String, Long> sourceBytes,
            int chunks) {}

    public record LabResponse(
            FaultState fault,
            Map<String, Object> benchmark,
            List<Map<String, Object>> samples,
            List<Map<String, Object>> links,
            List<Map<String, Object>> recentLinks,
            Map<String, Map<String, Long>> sourceStats) {}

    public record NetworkResponse(
            Map<String, Object> upload,
            Map<String, Object> download,
            int downloadWorkers,
            int activeDownloads,
            int workerLimitPerFile,
            int fileLimit,
            BandwidthResponse bandwidth) {}
}
