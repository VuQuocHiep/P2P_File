package vn.edu.p2p.client.dto.response;

/** Room summary returned by the tracker. */
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
        long created) {}
