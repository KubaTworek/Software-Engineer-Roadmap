package com.example.observability.server.bloom;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.BitSet;

public class SimpleBloomFilter {
    private final BitSet bits;
    private final int size;
    private final int hashCount;

    public SimpleBloomFilter(int size, int hashCount) {
        this.bits = new BitSet(size);
        this.size = size;
        this.hashCount = hashCount;
    }

    private SimpleBloomFilter(BitSet bits, int size, int hashCount) {
        this.bits = bits;
        this.size = size;
        this.hashCount = hashCount;
    }

    public void add(String value) {
        for (int idx : indexes(value)) bits.set(idx);
    }

    public boolean mightContain(String value) {
        for (int idx : indexes(value)) if (!bits.get(idx)) return false;
        return true;
    }

    public String encode() {
        return Base64.getEncoder().encodeToString(bits.toByteArray());
    }

    public int size() {
        return size;
    }

    public int hashCount() {
        return hashCount;
    }

    public static SimpleBloomFilter decode(String encoded, int size, int hashCount) {
        BitSet bitSet = BitSet.valueOf(Base64.getDecoder().decode(encoded == null ? "" : encoded));
        return new SimpleBloomFilter(bitSet, size, hashCount);
    }

    private int[] indexes(String value) {
        int[] indexes = new int[hashCount];
        byte[] digest = digest(value == null ? "" : value.toLowerCase());
        for (int i = 0; i < hashCount; i++) {
            int offset = (i * 4) % digest.length;
            int raw = ByteBuffer.wrap(digest, offset, 4).getInt();
            indexes[i] = Math.floorMod(raw, size);
        }
        return indexes;
    }

    private byte[] digest(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
