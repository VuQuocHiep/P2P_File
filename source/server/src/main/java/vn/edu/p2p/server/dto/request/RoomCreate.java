package vn.edu.p2p.server.dto.request;

/** Room creation: id is optional (generated when blank), password too. */
public record RoomCreate(String name, String id, String password) {}
