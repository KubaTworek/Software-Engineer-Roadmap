package com.example.autocomplete.service;

import org.springframework.stereotype.Component;

@Component
public class CanonicalKeyGenerator {
    private final TextNormalizer normalizer;

    public CanonicalKeyGenerator(TextNormalizer normalizer) {
        this.normalizer = normalizer;
    }

    public String canonicalKey(String text) {
        return normalizer.normalize(text).replace(" ", "");
    }
}
