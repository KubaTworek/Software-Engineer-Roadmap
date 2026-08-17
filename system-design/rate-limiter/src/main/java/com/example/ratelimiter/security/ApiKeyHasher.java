package com.example.ratelimiter.security;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * ApiKeyHasher odpowiada za hashowanie API key przed użyciem go w systemie.
 *
 * W Rate Limiterze API key może być używany jako część identyfikatora klienta,
 * np. do budowania klucza w Redisie:
 *
 * rl:api-key:{hash}:rule:{ruleId}
 *
 * Nie chcemy jednak używać surowego API key bezpośrednio, ponieważ API key
 * jest sekretem. Gdyby trafił do logów, metryk, Redisa albo debug endpointów,
 * byłby to poważny problem bezpieczeństwa.
 *
 * Dlatego zamiast oryginalnej wartości używamy SHA-256 hash.
 */
@Component
public class ApiKeyHasher {

    /**
     * Zwraca SHA-256 hash przekazanej wartości jako string hex.
     *
     * Przykład:
     *
     * input:
     *   "my-secret-api-key"
     *
     * output:
     *   "b6f...d91"
     *
     * W kontekście aplikacji ta metoda jest używana np. w RateLimitFilter,
     * zanim API key trafi do RequestContext.
     *
     * Dzięki temu dalsze komponenty aplikacji operują już na hashu,
     * a nie na surowym sekrecie.
     */
    public String sha256(String value) {
        /*
         * Jeśli API key nie został podany, zwracamy null.
         *
         * To pozwala późniejszej logice odróżnić:
         * - request bez API key,
         * - request z API key, który został zhashowany.
         *
         * Nie hashujemy pustych wartości, bo pusty string nie jest
         * sensowną tożsamością klienta.
         */
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            /*
             * MessageDigest to standardowy mechanizm JDK do liczenia hashy.
             *
             * SHA-256 jest deterministyczny:
             * ten sam API key zawsze da ten sam hash.
             *
             * To jest ważne, bo Rate Limiter musi stabilnie mapować
             * ten sam klucz API na ten sam identyfikator klienta.
             */
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            /*
             * API key zamieniamy na bajty w UTF-8.
             *
             * Dzięki jawnie wskazanemu kodowaniu wynik będzie spójny
             * niezależnie od domyślnego charsetu środowiska.
             */
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));

            /*
             * Hash binarny zamieniamy na format hex.
             *
             * Hex jest wygodny do:
             * - Redis keys,
             * - logów,
             * - debugowania,
             * - porównywania wartości.
             */
            return HexFormat.of().formatHex(hashed);

        } catch (NoSuchAlgorithmException e) {
            /*
             * SHA-256 jest standardowym algorytmem dostępnym w JVM.
             *
             * Jeśli z jakiegoś powodu nie jest dostępny, to jest błąd środowiska,
             * a nie zwykły błąd biznesowy. Dlatego rzucamy IllegalStateException.
             */
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}