package vn.edu.p2p.client.repository;

import java.time.Instant;
import java.util.Collection;
import org.springframework.stereotype.Repository;
import vn.edu.p2p.client.storage.PeerStorage;

/** The latest 100 local peer events. */
@Repository
public class PeerEventRepository {
    private final PeerStorage storage;

    public PeerEventRepository(PeerStorage storage) {
        this.storage = storage;
    }

    public Collection<String> findAll() {
        return storage.state().events;
    }

    public void append(String text) {
        synchronized (storage.monitor()) {
            storage.state().events.add(Instant.now() + " · " + text);
            if (storage.state().events.size() > 100) storage.state().events.remove(0);
        }
    }
}
