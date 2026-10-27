package vn.edu.p2p.common.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;

/** One request per TCP connection: magic, bounded JSON header, then length-prefixed raw chunk. */
public final class Wire {
    public static final int MAGIC = 0x504D3031; // PM01
    public static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    public static void write(DataOutputStream out, ChunkRequest request) throws IOException {
        byte[] json = JSON.writeValueAsBytes(request);
        out.writeInt(MAGIC); out.writeInt(json.length); out.write(json); out.flush();
    }
    public static ChunkRequest read(DataInputStream in) throws IOException {
        if (in.readInt() != MAGIC) throw new IOException("Sai phiên bản giao thức.");
        int length = in.readInt();
        if (length < 1 || length > 4096) throw new IOException("Header quá lớn.");
        byte[] bytes = in.readNBytes(length);
        if (bytes.length != length) throw new EOFException("Header thiếu dữ liệu.");
        return JSON.readValue(bytes, ChunkRequest.class);
    }
    private Wire() {}
}
