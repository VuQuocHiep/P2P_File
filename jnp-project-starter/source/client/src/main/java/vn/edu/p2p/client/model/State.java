package vn.edu.p2p.client.model;

import java.util.*;

public class State {
    public String peerId = "", owner = "", name = "", host = "127.0.0.1";
    public Map<String, Entry> library = new LinkedHashMap<>();
    public Map<String, Job> jobs = new LinkedHashMap<>();
    public List<String> events = new ArrayList<>();
}
