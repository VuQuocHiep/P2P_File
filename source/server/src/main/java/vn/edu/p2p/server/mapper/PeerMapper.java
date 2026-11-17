package vn.edu.p2p.server.mapper;

import java.util.List;
import org.springframework.stereotype.Component;
import vn.edu.p2p.server.dto.response.PeerResponse;
import vn.edu.p2p.server.dto.response.SourceResponse;
import vn.edu.p2p.server.entity.PeerEntity;

@Component
public class PeerMapper {
    public PeerResponse toResponse(PeerEntity peer, boolean online) {
        return new PeerResponse(
                peer.id,
                peer.owner,
                peer.name,
                peer.host,
                peer.port,
                peer.status,
                online,
                peer.lastSeen);
    }

    public SourceResponse toSourceResponse(PeerEntity peer, boolean online, List<Integer> chunks) {
        return new SourceResponse(
                peer.id,
                peer.owner,
                peer.name,
                peer.host,
                peer.port,
                peer.status,
                online,
                peer.lastSeen,
                chunks);
    }
}
