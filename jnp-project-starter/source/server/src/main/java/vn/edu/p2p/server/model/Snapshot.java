package vn.edu.p2p.server.model;

import java.util.*;

public class Snapshot {
    public Map<String, Account> accounts = new LinkedHashMap<>();
    public Map<String, Peer> peers = new LinkedHashMap<>();
    public Map<String, SharedFile> files = new LinkedHashMap<>();
    public List<Audit> audit = new ArrayList<>();
    public Map<String, Transfer> transfers = new LinkedHashMap<>();
}
