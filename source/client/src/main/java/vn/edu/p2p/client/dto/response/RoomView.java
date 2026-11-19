package vn.edu.p2p.client.dto.response;

/** Room summary returned by the tracker; membership is null when the viewer is not a member. */
public record RoomView(
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
