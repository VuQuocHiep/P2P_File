package vn.edu.p2p.client.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import vn.edu.p2p.client.dto.response.FileResponse;
import vn.edu.p2p.client.dto.response.SourceResponse;
import vn.edu.p2p.client.mapper.TrackerResponseMapper;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class PeerCatalogService {
    private final PeerContext context;
    private final TrackerResponseMapper trackerResponseMapper;

    public PeerCatalogService(PeerContext context, TrackerResponseMapper trackerResponseMapper) {
        this.context = context;
        this.trackerResponseMapper = trackerResponseMapper;
    }

    public List<FileResponse> catalog(String query) throws Exception {
        return trackerResponseMapper.toFileResponses(catalogData(query));
    }

    public List<SourceResponse> sources(String id) throws Exception {
        PeerSessionService.validId(id);
        JsonNode data = context.tracker.call("GET", "/api/files/" + id + "/sources", null);
        return trackerResponseMapper.toSourceResponses(data);
    }

    JsonNode catalogData(String query) throws Exception {
        return context.tracker.call(
                "GET", "/api/files?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8), null);
    }
}
