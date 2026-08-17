package com.example.observability.server.util;

public final class Validation {
    private Validation() {
    }

    public static String required(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        return value.trim();
    }

    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
