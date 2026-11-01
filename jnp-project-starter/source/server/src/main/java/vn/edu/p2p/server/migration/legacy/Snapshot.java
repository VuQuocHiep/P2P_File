package vn.edu.p2p.server.migration.legacy;

import java.util.*;
import vn.edu.p2p.server.model.Audit;
import vn.edu.p2p.server.model.Transfer;

/** Legacy registry JSON model, retained for one-time migration. */
public class Snapshot {
    public Map<String, Account> accounts = new LinkedHashMap<>();
    public Map<String, Peer> peers = new LinkedHashMap<>();
    public Map<String, SharedFile> files = new LinkedHashMap<>();
    public List<Audit> audit = new ArrayList<>();
    public Map<String, Transfer> transfers = new LinkedHashMap<>();
}
