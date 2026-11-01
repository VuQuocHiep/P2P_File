package vn.edu.p2p.server.migration.legacy;

import vn.edu.p2p.common.model.Manifest;
import java.util.*;

/** Legacy registry JSON model, retained for one-time migration. */
public class SharedFile {
    public Manifest manifest;
    public String status, submittedBy;
    public Map<String, List<Integer>> holders = new LinkedHashMap<>();
}
