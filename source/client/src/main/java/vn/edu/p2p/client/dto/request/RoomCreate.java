package vn.edu.p2p.client.dto.request;

/** Room creation; id and password are optional. */
public record RoomCreate(String name, String id, String password) {}
