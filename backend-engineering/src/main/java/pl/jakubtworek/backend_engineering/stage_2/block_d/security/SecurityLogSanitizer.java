package pl.jakubtworek.backend_engineering.stage_2.block_d.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Sanitizes structured fields; arbitrary request bodies should not be logged at all. */
public final class SecurityLogSanitizer {

    private static final Set<String> ALWAYS_SECRET = Set.of(
            "authorization", "cookie", "set-cookie", "password", "token", "api_key", "secret");

    public Map<String, String> sanitize(Map<String, LogField> fields) {
        if (fields == null) throw new IllegalArgumentException("fields are required");
        Map<String, String> result = new LinkedHashMap<>();
        fields.forEach((name, field) -> result.put(name, sanitize(name, field)));
        return Map.copyOf(result);
    }

    private static String sanitize(String name, LogField field) {
        if (name == null || name.isBlank() || field == null) throw new IllegalArgumentException("valid log field is required");
        String normalizedName = name.toLowerCase(Locale.ROOT).replace('-', '_');
        if (ALWAYS_SECRET.stream().anyMatch(normalizedName::contains)) return "[REDACTED_SECRET]";
        return switch (field.classification()) {
            case PUBLIC -> field.value();
            case STABLE_IDENTIFIER -> "id:" + digestPrefix(field.value());
            case PII -> "[REDACTED_PII]";
            case SECRET -> "[REDACTED_SECRET]";
        };
    }

    private static String digestPrefix(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 6);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the JDK", exception);
        }
    }

    public record LogField(String value, Classification classification) {
        public LogField {
            if (value == null || classification == null) throw new IllegalArgumentException("value and classification are required");
        }
    }

    public enum Classification {
        PUBLIC,
        STABLE_IDENTIFIER,
        PII,
        SECRET
    }
}
