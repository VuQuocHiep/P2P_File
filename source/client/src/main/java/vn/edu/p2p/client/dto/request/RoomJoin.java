package vn.edu.p2p.client.dto.request;

/** Join request; the password is only needed for protected rooms. */
public record RoomJoin(String password) {}
