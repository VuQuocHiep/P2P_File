package vn.edu.p2p.common.model;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** Content-addressed manifest. Chunk boundaries and hashes are part of its identity contract. */
public record Manifest(String id, String name, long size, int chunkSize, List<String> hashes) {
    public static final int CHUNK_SIZE = 256 * 1024;
    public static final long MAX_SIZE = 2L * 1024 * 1024 * 1024;

    public void validate() {
        if (id == null || !id.matches("[a-f0-9]{64}") || name == null || name.isBlank()
                || name.length() > 200 || name.contains("/") || name.contains("\\")
                || name.chars().anyMatch(Character::isISOControl)
                || size < 0 || size > MAX_SIZE || chunkSize != CHUNK_SIZE || hashes == null
                || hashes.size() != (size + chunkSize - 1) / chunkSize
                || hashes.stream().anyMatch(h -> h == null || !h.matches("[a-f0-9]{64}"))) {
            throw new IllegalArgumentException("Manifest không hợp lệ (tối đa 2 GiB).");
        }
    }
    public int length(int index) {
        if (index < 0 || index >= hashes.size()) throw new IllegalArgumentException("Chunk không tồn tại.");
        return (int) Math.min(chunkSize, size - (long) index * chunkSize);
    }
    public boolean sameContent(Manifest other) {
        return id.equals(other.id) && size == other.size && chunkSize == other.chunkSize && hashes.equals(other.hashes);
    }
    public static MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public static String hash(byte[] bytes) { return HexFormat.of().formatHex(digest().digest(bytes)); }
    public static String hash(Path path) throws IOException {
        var hash = digest();
        try (var in = Files.newInputStream(path)) {
            byte[] b = new byte[65536]; int n;
            while ((n = in.read(b)) != -1) hash.update(b, 0, n);
        }
        return HexFormat.of().formatHex(hash.digest());
    }
    public static Manifest create(Path path, String name) throws IOException {
        long size = Files.size(path);
        if (size > MAX_SIZE) throw new IllegalArgumentException("File tối đa 2 GiB.");
        var hashes = new ArrayList<String>(); var full = digest();
        try (var in = Files.newInputStream(path)) {
            byte[] b;
            while ((b = in.readNBytes(CHUNK_SIZE)).length != 0) { hashes.add(hash(b)); full.update(b); }
        }
        var m = new Manifest(HexFormat.of().formatHex(full.digest()), name, size, CHUNK_SIZE, List.copyOf(hashes));
        m.validate(); return m;
    }
}
