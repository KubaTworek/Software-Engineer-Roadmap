package com.example.autocomplete.privacy;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@Component
public class QueryPrivacyService {
    private static final Pattern EMAIL = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
    private static final Pattern PHONE = Pattern.compile("\\b\\+?\\d[\\d -]{7,}\\d\\b");
    private static final Pattern LONG_NUMBER = Pattern.compile("\\b\\d{8,}\\b");
    private final byte[] hmacKey;

    public QueryPrivacyService(@Value("${app.privacy.hmac-key:local-development-key-change-me}") String hmacKey) {
        if (hmacKey == null || hmacKey.length() < 16) {
            throw new IllegalArgumentException("Privacy HMAC key must have at least 16 characters");
        }
        this.hmacKey = hmacKey.getBytes(StandardCharsets.UTF_8);
    }

    public String redactPii(String query) {
        if (query == null) return "";
        String redacted = EMAIL.matcher(query).replaceAll("[email]");
        // Długi, ciągły numer klasyfikujemy przed numerem telefonu. W przeciwnym
        // razie szerszy regex PHONE pochłonąłby np. numer karty i błędnie opisał PII.
        redacted = LONG_NUMBER.matcher(redacted).replaceAll("[number]");
        redacted = PHONE.matcher(redacted).replaceAll("[phone]");
        return redacted;
    }

    public String hashUserId(String userId) {
        return hash(userId == null ? "anonymous" : userId);
    }

    public String hashIp(String ip) {
        return hash(ip == null ? "unknown" : ip);
    }

    public boolean containsPii(String text) {
        if (text == null || text.isBlank()) return false;
        return EMAIL.matcher(text).find()
                || PHONE.matcher(text).find()
                || LONG_NUMBER.matcher(text).find();
    }

    private String hash(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacKey, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8))).substring(0, 24);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot hash value", e);
        }
    }
}
