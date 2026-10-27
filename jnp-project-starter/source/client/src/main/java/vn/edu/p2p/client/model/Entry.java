package vn.edu.p2p.client.model;

import vn.edu.p2p.common.model.Manifest;
import java.util.*;

public class Entry {
    public Manifest manifest;
    public boolean complete, sharing = true;
    public String catalogStatus = "PENDING";
    public Set<Integer> chunks = new TreeSet<>();
}
