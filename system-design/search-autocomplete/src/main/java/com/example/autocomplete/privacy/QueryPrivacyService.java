package com.example.autocomplete.privacy;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.regex.Pattern;

@Component
public class QueryPrivacyService {
    private static final Pattern EMAIL = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}");
    private static final Pattern PHONE = Pattern.compile("\\b\\+?\\d[\\d -]{7,}\\d\\b");
    private static final Pattern LONG_NUMBER = Pattern.compile("\\b\\d{8,}\\b");

    public String redactPii(String query) {
        if (query == null) return "";
        String redacted = EMAIL.matcher(query).replaceAll("[email]");
        redacted = PHONE.matcher(redacted).replaceAll("[phone]");
        redacted = LONG_NUMBER.matcher(redacted).replaceAll("[number]");
        return redacted;
    }

    public String hashUserId(String userId) {
        return hash(userId == null ? "anonymous" : userId);
    }

    public String hashIp(String ip) {
        return hash(ip == null ? "unknown" : ip);
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8))).substring(0, 16);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot hash value", e);
        }
    }
}
