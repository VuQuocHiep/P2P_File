package vn.edu.p2p.server.repository;

import vn.edu.p2p.server.model.TrackerModels.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.*;

/** Durable registry. Service serializes mutations; sessions and transfer tickets stay in memory. */
@Repository
public class TrackerState {
    final Snapshot data;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public TrackerState(JdbcTemplate jdbc, ObjectMapper json) throws Exception {
        this.jdbc = jdbc; this.json = json;
        jdbc.execute("CREATE TABLE IF NOT EXISTS registry (id INT PRIMARY KEY, payload CLOB NOT NULL)");
        List<String> rows = jdbc.query("SELECT payload FROM registry WHERE id=1", (rs, row) -> rs.getString(1));
        data = rows.isEmpty() ? new Snapshot() : json.readValue(rows.get(0), Snapshot.class);
        data.peers.values().forEach(p -> p.lastSeen = 0);
    }
    public void save() {
        try { jdbc.update("MERGE INTO registry (id,payload) KEY(id) VALUES (1,?)", json.writeValueAsString(data)); }
        catch (Exception e) { throw new IllegalStateException("Không lưu được registry.", e); }
    }
    public Snapshot snapshot() { return data; }
    public void log(String actor, String action, String target) {
        data.audit.add(new Audit(System.currentTimeMillis(), actor, action, target));
        if (data.audit.size() > 2000) data.audit.remove(0);
    }
}
