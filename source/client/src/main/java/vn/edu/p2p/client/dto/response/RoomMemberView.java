package vn.edu.p2p.client.dto.response;

/** Membership row: PENDING, APPROVED, REJECTED or OWNER. */
public record RoomMemberView(String username, String status, long requestedAt) {}
