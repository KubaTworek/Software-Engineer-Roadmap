package com.example.autocomplete.personalization;

import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Prosty magazyn profili użytkowników.
 *
 * W kontekście autocomplete profil użytkownika służy do personalizacji rankingu.
 *
 * Przykład:
 * - użytkownik "u-apple" częściej dostanie wyżej sugestie związane z Apple,
 * - użytkownik "u-dev" częściej dostanie wyżej sugestie związane z Javą/Dockerem,
 * - użytkownik "u-gaming" częściej dostanie wyżej sugestie gamingowe.
 *
 * To jest implementacja in-memory, dobra dla demo i etapu edukacyjnego.
 * W produkcji ten komponent powinien czytać dane z:
 * - Feature Store,
 * - Redis/DynamoDB/Cassandra,
 * - profilu użytkownika,
 * - systemu rekomendacji,
 * - event pipeline'u z historią zachowań.
 */
@Component
public class UserProfileStore {

    /**
     * Mapa profili użytkowników.
     *
     * Klucz:
     * - userId, np. "u-apple"
     *
     * Wartość:
     * - UserProfile zawierający preferencje użytkownika.
     *
     * W tej wersji dane są statyczne i trzymane w pamięci aplikacji.
     */
    private final Map<String, UserProfile> profiles = new HashMap<>();

    public UserProfileStore() {

        /*
         * Profil użytkownika zainteresowanego produktami Apple.
         *
         * recentQueries:
         * - ostatnie lub typowe zapytania użytkownika.
         *
         * preferredCategories:
         * - kategorie, które powinny dostać boost w rankingu.
         *
         * preferredBrands:
         * - marki, które powinny dostać boost w rankingu.
         */
        profiles.put(
                "u-apple",
                new UserProfile(
                        "u-apple",
                        List.of("iphone 15 pro", "macbook pro"),
                        Set.of("electronics", "apple"),
                        Set.of("apple")
                )
        );

        /*
         * Profil użytkownika technicznego/developerskiego.
         *
         * Dzięki temu dla query typu "jav" albo "doc"
         * sugestie związane z Java, Spring Boot, Dockerem czy Kubernetesem
         * mogą dostać wyższy personalization score.
         */
        profiles.put(
                "u-dev",
                new UserProfile(
                        "u-dev",
                        List.of("java spring boot", "docker compose"),
                        Set.of("software", "developer-tools"),
                        Set.of("java", "docker")
                )
        );

        /*
         * Profil użytkownika gamingowego.
         *
         * Dla takiego użytkownika sugestie typu:
         * - Sony PlayStation 5,
         * - Nintendo Switch,
         * - akcesoria gamingowe
         *
         * mogą być wyżej rankowane niż ogólne sugestie.
         */
        profiles.put(
                "u-gaming",
                new UserProfile(
                        "u-gaming",
                        List.of("ps5", "nintendo switch"),
                        Set.of("gaming"),
                        Set.of("sony", "nintendo")
                )
        );
    }

    /**
     * Zwraca profil użytkownika.
     *
     * Jeśli użytkownik istnieje w mapie, zwracamy jego profil.
     * Jeśli nie istnieje, zwracamy profil anonimowy.
     *
     * Dzięki temu ranking zawsze dostaje jakiś profil i nie musi obsługiwać nulla.
     */
    public UserProfile getProfile(String id) {
        return profiles.getOrDefault(id, UserProfile.anonymous(id));
    }
}