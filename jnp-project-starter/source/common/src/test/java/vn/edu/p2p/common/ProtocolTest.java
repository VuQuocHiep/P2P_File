package vn.edu.p2p.common;

import vn.edu.p2p.common.protocol.Wire;
import vn.edu.p2p.common.model.Manifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class ProtocolTest {
    @TempDir Path temp;
    @Test void hashesMatchAtChunkBoundaries() throws Exception {
        byte[] bytes = new byte[Manifest.CHUNK_SIZE + 37]; new java.util.Random(42).nextBytes(bytes);
        Path file = temp.resolve("sample.bin"); Files.write(file, bytes);
        Manifest m = Manifest.create(file, "sample.bin");
        assertEquals(2, m.hashes().size()); assertEquals(37, m.length(1));
        assertEquals(Manifest.hash(bytes), m.id());
        assertEquals(Manifest.hash(java.util.Arrays.copyOf(bytes, Manifest.CHUNK_SIZE)), m.hashes().get(0));
        assertThrows(IllegalArgumentException.class, () -> m.length(2));
        assertThrows(IllegalArgumentException.class, () -> new Manifest(m.id(), "../secret", m.size(), m.chunkSize(), m.hashes()).validate());
    }
    @Test void boundedFramesRejectTruncationAndOversizedHeaders() throws Exception {
        var bytes = new ByteArrayOutputStream(); var out = new DataOutputStream(bytes);
        out.writeInt(Wire.MAGIC); out.writeInt(100_000);
        assertThrows(IOException.class, () -> Wire.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
        bytes.reset(); out.writeInt(Wire.MAGIC); out.writeInt(20); out.writeByte(1);
        assertThrows(EOFException.class, () -> Wire.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
        bytes.reset(); var request = new Wire.Request("ticket", "file", 7); Wire.write(out, request);
        assertEquals(request, Wire.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
    }
}
