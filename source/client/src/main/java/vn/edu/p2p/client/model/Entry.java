package vn.edu.p2p.client.model;

import vn.edu.p2p.common.model.Manifest;
import java.util.*;

public class Entry {
    public Manifest manifest;
    public boolean complete, sharing = true;
    public boolean scanConsent;
    public String catalogStatus = "PENDING";

    /** Room this file was published to; null keeps it in the public catalog. */
    public String roomId;

    public Set<Integer> chunks = new TreeSet<>();
}
