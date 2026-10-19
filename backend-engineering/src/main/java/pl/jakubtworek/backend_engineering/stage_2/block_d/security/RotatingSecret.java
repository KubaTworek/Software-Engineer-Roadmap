package pl.jakubtworek.backend_engineering.stage_2.block_d.security;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

/** Holds current and previous credential during an explicit rotation grace period. */
public final class RotatingSecret implements AutoCloseable {

    private SecretVersion current;
    private SecretVersion previous;

    public RotatingSecret(String version, char[] value) {
        this.current = SecretVersion.create(version, value);
    }

    public synchronized void rotate(String nextVersion, char[] nextValue) {
        if (current.version().equals(nextVersion)) throw new IllegalArgumentException("secret version must change");
        if (previous != null) previous.destroy();
        previous = current;
        current = SecretVersion.create(nextVersion, nextValue);
    }

    public synchronized boolean matches(char[] candidate) {
        if (candidate == null) return false;
        byte[] bytes = utf8(candidate);
        try {
            return current.matches(bytes) || (previous != null && previous.matches(bytes));
        } finally {
            Arrays.fill(bytes, (byte) 0);
        }
    }

    public synchronized void retirePrevious() {
        if (previous != null) {
            previous.destroy();
            previous = null;
        }
    }

    public synchronized String activeVersion() {
        return current.version();
    }

    public synchronized boolean gracePeriodActive() {
        return previous != null;
    }

    @Override
    public synchronized void close() {
        current.destroy();
        if (previous != null) previous.destroy();
        previous = null;
    }

    @Override
    public String toString() {
        return "RotatingSecret[REDACTED]";
    }

    private record SecretVersion(String version, byte[] digest) {

        private static SecretVersion create(String version, char[] value) {
            if (version == null || version.isBlank()) throw new IllegalArgumentException("version is required");
            if (value == null || value.length < 16) throw new IllegalArgumentException("secret must contain at least 16 characters");
            byte[] bytes = utf8(value);
            try {
                return new SecretVersion(version, sha256(bytes));
            } finally {
                Arrays.fill(bytes, (byte) 0);
            }
        }

        private boolean matches(byte[] candidate) {
            return MessageDigest.isEqual(digest, sha256(candidate));
        }

        private void destroy() {
            Arrays.fill(digest, (byte) 0);
        }

        private static byte[] sha256(byte[] value) {
            try {
                return MessageDigest.getInstance("SHA-256").digest(value);
            } catch (java.security.NoSuchAlgorithmException exception) {
                throw new IllegalStateException("SHA-256 is required by the JDK", exception);
            }
        }
    }

    private static byte[] utf8(char[] value) {
        ByteBuffer encoded = StandardCharsets.UTF_8.encode(CharBuffer.wrap(value));
        byte[] bytes = new byte[encoded.remaining()];
        encoded.get(bytes);
        if (encoded.hasArray()) Arrays.fill(encoded.array(), (byte) 0);
        return bytes;
    }
}
