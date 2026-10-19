package com.example.urlshortener.idempotency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Builds a bounded storage key without leaking the caller's raw key into the database. */
public final class IdempotencyScope {
    private IdempotencyScope() {}

    public static String storageKey(String actor, String operation, String rawKey) {
        if (rawKey == null || rawKey.isBlank() || rawKey.length() > 128
                || !rawKey.matches("[A-Za-z0-9._:-]+")) {
            throw new IllegalArgumentException("Idempotency-Key must contain 1-128 safe ASCII characters");
        }
        String material = actor + '\0' + operation + '\0' + rawKey;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
