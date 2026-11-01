package vn.edu.p2p.server.entity;

import jakarta.persistence.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import vn.edu.p2p.common.model.Manifest;
import vn.edu.p2p.server.entity.converter.HoldersConverter;
import vn.edu.p2p.server.entity.converter.ManifestConverter;

@Entity
@Table(name = "tracker_files")
public class FileEntity extends OrderedEntity {
    @Id public String id;

    @Lob
    @Convert(converter = ManifestConverter.class)
    public Manifest manifest;

    public String status, submittedBy;

    @Lob
    @Convert(converter = vn.edu.p2p.server.entity.converter.MalwareReportConverter.class)
    public vn.edu.p2p.server.dto.response.MalwareReport malware;

    @Lob
    @Convert(converter = vn.edu.p2p.server.entity.converter.StringSetConverter.class)
    public java.util.Set<String> scanConsentPeers = new java.util.HashSet<>();

    public String scanStage, scanRequestId, scanActor, scanLeasePeer, scanLeaseToken;
    public Long scanLeaseUntil, scanNextAt;
    public Integer scanFailures;

    @Lob
    @Convert(converter = HoldersConverter.class)
    public Map<String, List<Integer>> holders = new LinkedHashMap<>();
}
