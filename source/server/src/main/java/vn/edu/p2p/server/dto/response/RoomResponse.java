package vn.edu.p2p.server.dto.response;

/**
 * Room summary for the peer UI. membership is OWNER, APPROVED or PENDING; mine is true for the
 * owner.
 */
public record RoomResponse(
        String id,
        String name,
        String owner,
        boolean mine,
        String membership,
        boolean hasPassword,
        long members,
        long pendingRequests,
        long files,
        long created) {}
