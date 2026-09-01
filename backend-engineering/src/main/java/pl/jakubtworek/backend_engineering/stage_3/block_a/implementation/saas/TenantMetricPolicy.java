package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.saas;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;

/**
 * Produces bounded metric dimensions. Raw tenant id belongs in secured logs,
 * traces or audit records, not in a Prometheus label.
 */
public final class TenantMetricPolicy {

    private final int bucketCount;

    public TenantMetricPolicy(int bucketCount) {
        if (bucketCount < 1 || bucketCount > 1_024) {
            throw new IllegalArgumentException("bucketCount must be between 1 and 1024");
        }
        this.bucketCount = bucketCount;
    }

    public Map<String, String> tags(TenantId tenantId, Plan plan, String outcome) {
        return Map.of(
                "tenant_plan", plan.name().toLowerCase(),
                "tenant_bucket", Integer.toString(bucket(tenantId)),
                "outcome", outcome);
    }

    private int bucket(TenantId tenantId) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(tenantId.value().getBytes(StandardCharsets.UTF_8));
            String prefix = HexFormat.of().formatHex(digest, 0, 4);
            return (int) (Long.parseUnsignedLong(prefix, 16) % bucketCount);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the JDK", exception);
        }
    }

    public enum Plan {
        FREE,
        STANDARD,
        ENTERPRISE
    }
}
