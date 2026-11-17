package vn.edu.p2p.server.dto.response;

import java.util.List;
import vn.edu.p2p.server.model.Audit;
import vn.edu.p2p.server.model.Transfer;

public record DashboardResponse(
        List<UserResponse> accounts,
        List<PeerResponse> peers,
        List<FileResponse> files,
        List<Transfer> transfers,
        List<Audit> audit) {}
