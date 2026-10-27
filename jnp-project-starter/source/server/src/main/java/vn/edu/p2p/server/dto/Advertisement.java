package vn.edu.p2p.server.dto;

import vn.edu.p2p.common.model.Manifest;
import java.util.List;

public record Advertisement(String peerId, Manifest manifest, List<Integer> chunks) {}
