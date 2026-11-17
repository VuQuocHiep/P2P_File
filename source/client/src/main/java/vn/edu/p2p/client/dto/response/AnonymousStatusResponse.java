package vn.edu.p2p.client.dto.response;

public record AnonymousStatusResponse(boolean authenticated) implements PeerStatusView {}
