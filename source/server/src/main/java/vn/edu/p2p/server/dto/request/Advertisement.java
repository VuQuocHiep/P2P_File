package vn.edu.p2p.server.dto.request;

import vn.edu.p2p.common.model.Manifest;
import java.util.List;

/**
 * File advertisement. roomId is optional: null keeps the file in the public catalog, while a
 * non-null value scopes it to a room the submitting account belongs to.
 */
public record Advertisement(
        String peerId, Manifest manifest, List<Integer> chunks, boolean scanConsent, String roomId) {
    public Advertisement(String peerId, Manifest manifest, List<Integer> chunks) {
        this(peerId, manifest, chunks, false, null);
    }

    public Advertisement(String peerId, Manifest manifest, List<Integer> chunks, boolean scanConsent) {
        this(peerId, manifest, chunks, scanConsent, null);
    }
}
