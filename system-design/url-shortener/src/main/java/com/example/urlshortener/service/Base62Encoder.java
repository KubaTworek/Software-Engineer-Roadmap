package com.example.urlshortener.service;

import org.springframework.stereotype.Component;

@Component
public class Base62Encoder {
    private static final char[] ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();
    private static final int BASE = ALPHABET.length;

    public String encode(long value) {
        if (value < 0) throw new IllegalArgumentException("Value must be non-negative");
        if (value == 0) return String.valueOf(ALPHABET[0]);
        StringBuilder result = new StringBuilder();
        long current = value;
        while (current > 0) {
            int remainder = (int) (current % BASE);
            result.append(ALPHABET[remainder]);
            current = current / BASE;
        }
        return result.reverse().toString();
    }
}
