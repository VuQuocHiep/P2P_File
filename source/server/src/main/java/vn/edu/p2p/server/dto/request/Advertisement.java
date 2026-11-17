package vn.edu.p2p.server.dto.request;

import vn.edu.p2p.common.model.Manifest;
import java.util.List;

public record Advertisement(String peerId, Manifest manifest, List<Integer> chunks, boolean scanConsent) {
    public Advertisement(String peerId, Manifest manifest, List<Integer> chunks) {
        this(peerId, manifest, chunks, false);
    }
}
