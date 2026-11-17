package vn.edu.p2p.server.dto.request;

/**
 * Owner room update. A null field keeps the current value; an empty password clears the password.
 */
public record RoomUpdate(String name, String password) {}
