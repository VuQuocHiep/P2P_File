package vn.edu.p2p.client.dto.request;

/** Owner room update; null keeps the current value, empty password clears it. */
public record RoomUpdate(String name, String password) {}
