package com.example.observability.server.tenant;

import com.example.observability.server.auth.AuthContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * Serwis wyszukujący dynamiczne API keys zapisane w bazie.
 *
 * Ten komponent jest częścią self-service tenant management.
 *
 * W odróżnieniu od statycznych/demo kluczy zapisanych w konfiguracji,
 * tutaj klucze są tworzone runtime'owo dla konkretnego tenanta
 * i przechowywane w tabeli tenant_api_keys.
 *
 * Główna rola:
 * - przyjąć jawny token z requestu,
 * - policzyć jego hash SHA-256,
 * - znaleźć aktywny klucz w bazie,
 * - zbudować AuthContext z tenantId, nazwą klucza i rolami.
 *
 * Ważne bezpieczeństwo:
 * baza nie przechowuje jawnego tokena, tylko token_hash.
 */
@Service
public class DynamicApiKeyService {

    /**
     * Dostęp do bazy przez JDBC.
     *
     * Używany do lookupu aktywnego API key po token_hash.
     */
    private final JdbcTemplate jdbc;

    public DynamicApiKeyService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Próbuje znaleźć aktywny API key dla podanego tokena.
     *
     * Zwraca:
     * - Optional<AuthContext> z tenantem i rolami, jeśli token jest poprawny,
     * - Optional.empty(), jeśli token jest pusty, nie istnieje, jest nieaktywny
     *   albo wystąpi błąd lookupu.
     *
     * Przepływ:
     * 1. Odrzuca null/pusty token.
     * 2. Liczy SHA-256 tokena.
     * 3. Szuka rekordu w tenant_api_keys po token_hash i status='active'.
     * 4. Mapuje wynik na AuthContext.
     * 5. Parsuje role zapisane w bazie.
     */
    public Optional<AuthContext> find(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }

        try {
            /*
             * Token jawny nigdy nie jest porównywany bezpośrednio z bazą.
             * Do bazy trafia tylko hash, więc tutaj liczymy hash z tokena
             * otrzymanego z nagłówka requestu.
             */
            String hash = sha256(token);

            /*
             * Szukamy tylko aktywnych kluczy.
             *
             * LIMIT 1 wystarcza, bo token_hash powinien być unikalny.
             * Jeśli nie jest unikalny, to jest problem modelu danych.
             */
            List<AuthContext> contexts = jdbc.query("""
                    SELECT tenant_id, name, roles
                    FROM tenant_api_keys
                    WHERE token_hash=? AND status='active'
                    LIMIT 1
                    """,
                    (rs, rowNum) -> new AuthContext(
                            rs.getString("tenant_id"),
                            rs.getString("name"),
                            parseRoles(rs.getObject("roles"))
                    ),
                    hash
            );

            return contexts.stream().findFirst();

        } catch (Exception ignored) {
            /*
             * Auth lookup nie powinien wywalać całego requestu błędem 500,
             * jeśli np. baza chwilowo nie odpowie albo format roles jest błędny.
             *
             * Z perspektywy auth bezpieczniejszy fallback to brak uwierzytelnienia.
             *
             * Uwaga produkcyjna:
             * błędu nie powinno się całkiem ignorować.
             * Warto logować go na debug/warn i mieć metrykę auth_lookup_errors.
             */
            return Optional.empty();
        }
    }

    /**
     * Parsuje role zapisane w bazie do Set<String>.
     *
     * Obsługiwane przypadki:
     * - null -> fallback viewer,
     * - String[] -> bezpośrednia konwersja do Set,
     * - inny typ -> parsowanie po stringu.
     *
     * Role są potem używane przez RBAC, np.:
     * - viewer,
     * - writer,
     * - admin,
     * - platform_admin.
     */
    private Set<String> parseRoles(Object raw) {
        if (raw == null) {
            return Set.of("viewer");
        }

        /*
         * Część driverów JDBC może zwrócić tablicę Stringów
         * dla kolumn typu Array(String).
         */
        if (raw instanceof String[] arr) {
            return new HashSet<>(Arrays.asList(arr));
        }

        /*
         * Fallback dla driverów, które zwracają role jako tekst,
         * np. "[admin, writer]" albo "['admin','writer']".
         *
         * To jest tolerancyjne dla MVP, ale dość kruche.
         */
        String s = String.valueOf(raw)
                .replace("[", "")
                .replace("]", "")
                .replace("'", "");

        Set<String> roles = new HashSet<>();

        for (String part : s.split(",")) {
            if (!part.isBlank()) {
                roles.add(part.trim());
            }
        }

        /*
         * Jeśli role są puste albo nie dało się ich sparsować,
         * minimalny bezpieczny fallback to viewer.
         */
        return roles.isEmpty()
                ? Set.of("viewer")
                : roles;
    }

    /**
     * Liczy SHA-256 z jawnego tokena API key.
     *
     * Wynik jest zapisywany jako hex string.
     *
     * Ten hash musi być zgodny z hashem zapisanym przy tworzeniu API key.
     */
    private String sha256(String input) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");

        byte[] digest = md.digest(
                input.getBytes(StandardCharsets.UTF_8)
        );

        StringBuilder sb = new StringBuilder();

        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }

        return sb.toString();
    }
}