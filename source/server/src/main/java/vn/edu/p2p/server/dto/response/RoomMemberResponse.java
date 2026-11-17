package vn.edu.p2p.server.dto.response;

/** One membership row; status is PENDING, APPROVED, REJECTED or OWNER. */
public record RoomMemberResponse(String username, String status, long requestedAt) {}
