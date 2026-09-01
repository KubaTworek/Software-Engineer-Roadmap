package pl.jakubtworek.backend_engineering.stage_2.block_c.progressive_delivery;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Deterministic percentage flag where the kill switch always has precedence. */
public record FeatureFlag(String name, boolean enabled, boolean killSwitch, int rolloutPercent) {

    public FeatureFlag {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("flag name is required");
        if (rolloutPercent < 0 || rolloutPercent > 100) throw new IllegalArgumentException("rolloutPercent must be 0..100");
    }

    public boolean enabledFor(String stableSubjectId) {
        if (killSwitch || !enabled) return false;
        if (stableSubjectId == null || stableSubjectId.isBlank()) return false;
        return bucket(name + ':' + stableSubjectId) < rolloutPercent;
    }

    private static int bucket(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return Math.floorMod(((digest[0] & 0xff) << 8) | (digest[1] & 0xff), 100);
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the JDK", exception);
        }
    }
}
