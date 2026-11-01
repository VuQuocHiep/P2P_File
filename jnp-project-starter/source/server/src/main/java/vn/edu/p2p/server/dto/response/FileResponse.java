package vn.edu.p2p.server.dto.response;

import vn.edu.p2p.common.model.Manifest;

public record FileResponse(
        Manifest manifest, String status, String submittedBy, long sources, int holders,
        MalwareReport malware, String scanStage) {}
