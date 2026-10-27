package vn.edu.p2p.server.model;

import vn.edu.p2p.common.model.Manifest;
import java.util.*;

public class SharedFile {
    public Manifest manifest;
    public String status, submittedBy;
    public Map<String, List<Integer>> holders = new LinkedHashMap<>();
}
