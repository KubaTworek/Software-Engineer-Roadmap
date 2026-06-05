package com.example.autocomplete.service;

import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class TextNormalizer {
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_SEARCH_CHARS = Pattern.compile("[^a-z0-9\\s]");
    private static final Pattern MULTIPLE_SPACES = Pattern.compile("\\s+");

    public String normalize(String input) {
        if (input == null) {
            return "";
        }

        String normalized = input.toLowerCase(Locale.ROOT);
        normalized = replacePolishCharacters(normalized);
        normalized = Normalizer.normalize(normalized, Normalizer.Form.NFD);
        normalized = DIACRITICS.matcher(normalized).replaceAll("");
        normalized = NON_SEARCH_CHARS.matcher(normalized).replaceAll(" ");
        normalized = MULTIPLE_SPACES.matcher(normalized).replaceAll(" ");

        return normalized.trim();
    }

    private String replacePolishCharacters(String input) {
        return input
                .replace("ą", "a")
                .replace("ć", "c")
                .replace("ę", "e")
                .replace("ł", "l")
                .replace("ń", "n")
                .replace("ó", "o")
                .replace("ś", "s")
                .replace("ź", "z")
                .replace("ż", "z");
    }
}
