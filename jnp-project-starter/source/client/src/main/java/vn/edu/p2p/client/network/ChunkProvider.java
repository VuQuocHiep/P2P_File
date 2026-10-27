package vn.edu.p2p.client.network;

import vn.edu.p2p.common.protocol.ChunkRequest;

@FunctionalInterface
public interface ChunkProvider { byte[] read(ChunkRequest request) throws Exception; }
