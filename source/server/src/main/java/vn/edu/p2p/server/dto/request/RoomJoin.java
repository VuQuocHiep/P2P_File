package vn.edu.p2p.server.dto.request;

/** Join request body; password is only required for protected rooms. */
public record RoomJoin(String password) {}
