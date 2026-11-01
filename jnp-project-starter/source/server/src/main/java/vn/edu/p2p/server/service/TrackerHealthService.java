package vn.edu.p2p.server.service;

import org.springframework.stereotype.Service;
import vn.edu.p2p.server.dto.response.HealthResponse;

@Service
public class TrackerHealthService {
    public HealthResponse health() {
        return new HealthResponse("ok", "PeerMesh Tracker");
    }
}
