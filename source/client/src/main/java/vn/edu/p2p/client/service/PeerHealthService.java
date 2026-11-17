package vn.edu.p2p.client.service;

import org.springframework.stereotype.Service;
import vn.edu.p2p.client.dto.response.HealthResponse;

@Service
public class PeerHealthService {
    public HealthResponse health() {
        return new HealthResponse("ok", "PeerMesh Peer");
    }
}
