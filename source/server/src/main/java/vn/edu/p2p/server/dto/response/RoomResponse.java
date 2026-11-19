package vn.edu.p2p.server.dto.response;

/**
 * Room summary for the peer UI and the admin dashboard. membership is OWNER, APPROVED, PENDING,
 * REJECTED, or null when the viewer is not involved in the room; mine is true for the owner.
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
        long created,
        String status) {}
