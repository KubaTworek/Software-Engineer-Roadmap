package com.example.autocomplete.personalization;

import java.util.List;
import java.util.Set;

public record UserProfile(String userId, List<String> recentQueries, Set<String> preferredCategories,
                          Set<String> preferredBrands) {
    public static UserProfile anonymous(String id) {
        return new UserProfile(id, List.of(), Set.of(), Set.of());
    }
}
