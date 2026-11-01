package vn.edu.p2p.client.repository;

import org.springframework.stereotype.Repository;
import vn.edu.p2p.client.storage.PeerStorage;

/** Local account and device metadata. Changes are persisted by PeerStorage.saveCheckpoint(). */
@Repository
public class PeerProfileRepository {
    private final PeerStorage storage;

    public PeerProfileRepository(PeerStorage storage) {
        this.storage = storage;
    }

    public String getOwner() {
        return storage.state().owner;
    }

    public String getName() {
        return storage.state().name;
    }

    public String getHost() {
        return storage.state().host;
    }

    public String getPeerId() {
        return storage.state().peerId;
    }

    public void update(String owner, String name, String host) {
        storage.state().owner = owner;
        storage.state().name = name;
        storage.state().host = host;
    }

    public void updatePeerId(String peerId) {
        storage.state().peerId = peerId;
    }
}
