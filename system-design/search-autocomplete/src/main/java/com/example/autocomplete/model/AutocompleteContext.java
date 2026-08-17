package com.example.autocomplete.model;

public record AutocompleteContext(String userId, String sessionId, String locale, String country, String category,
                                  String rawQuery, String normalizedQuery, String clientIp) {
    public String safeUserId() {
        return userId == null || userId.isBlank() ? "anonymous" : userId;
    }

    public String safeSessionId() {
        return sessionId == null || sessionId.isBlank() ? "default" : sessionId;
    }

    public String safeLocale() {
        return locale == null || locale.isBlank() ? "en-US" : locale;
    }

    public String safeCountry() {
        return country == null || country.isBlank() ? "US" : country;
    }

    public String safeClientIp() {
        return clientIp == null || clientIp.isBlank() ? "unknown" : clientIp;
    }
}
