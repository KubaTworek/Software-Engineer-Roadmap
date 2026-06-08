package com.example.ratelimiter.core;

import com.example.ratelimiter.config.DynamicConfigService;
import com.example.ratelimiter.config.RateLimiterProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * RuleMatcher odpowiada za wybór reguł rate limitingu pasujących
 * do konkretnego requestu.
 *
 * Ta klasa nie konsumuje tokenów i nie podejmuje decyzji allow/deny.
 * Jej zadanie jest wcześniejsze:
 *
 * RequestContext
 *   -> RuleMatcher
 *   -> lista pasujących reguł
 *   -> RateLimiterEngine
 *   -> RedisTokenBucketLimiter / fallback
 *
 * Przykład:
 *
 * Request:
 *   POST /api/payments
 *   tenantId = tenant-1
 *   userId = user-123
 *   plan = FREE
 *
 * RuleMatcher może zwrócić:
 *   - global limit,
 *   - tenant limit,
 *   - user limit,
 *   - plan FREE limit,
 *   - endpoint POST /api/payments limit.
 *
 * Dopiero RateLimiterEngine sprawdzi te reguły i zdecyduje,
 * czy request ma zostać przepuszczony.
 */
@Component
public class RuleMatcher {

    /**
     * DynamicConfigService jest źródłem aktualnych reguł.
     *
     * Dzięki temu matcher nie czyta bezpośrednio z application.yml,
     * bazy danych ani cache'a. Dostaje już aktualny zestaw konfiguracji.
     *
     * To pozwala później zmieniać reguły dynamicznie przez Admin API,
     * bez zmieniania logiki dopasowywania.
     */
    private final DynamicConfigService configService;

    public RuleMatcher(DynamicConfigService configService) {
        this.configService = configService;
    }

    /**
     * Zwraca listę reguł pasujących do danego RequestContext.
     *
     * RequestContext zawiera dane takie jak:
     * - metoda HTTP,
     * - path,
     * - client IP,
     * - apiKeyHash,
     * - userId,
     * - tenantId,
     * - plan.
     *
     * Każda reguła z konfiguracji jest sprawdzana przez matches(...).
     *
     * Ważne:
     * ta metoda tylko wybiera reguły. Nie sortuje ich po priority
     * i nie rozwiązuje konfliktów override'ów, jeśli DynamicConfigService
     * nie robi tego wcześniej.
     *
     * Jeżeli kolejność reguł ma znaczenie, warto tu dodać sortowanie po priority.
     */
    public List<RateLimiterProperties.Rule> match(RequestContext ctx) {
        return configService.allRules().stream()
                .filter(rule -> matches(rule, ctx))
                .toList();
    }

    /**
     * Sprawdza, czy konkretna reguła pasuje do requestu.
     *
     * Sposób dopasowania zależy od typu reguły:
     *
     * GLOBAL:
     * - pasuje zawsze.
     *
     * TENANT:
     * - pasuje, gdy tenantId z reguły jest równy tenantId z requestu.
     *
     * USER:
     * - pasuje, gdy userId z reguły jest równy userId z requestu.
     *
     * API_KEY:
     * - pasuje, gdy hash API key z reguły jest równy hash z requestu.
     *
     * PLAN:
     * - pasuje, gdy plan z reguły jest równy planowi z requestu.
     *
     * ENDPOINT:
     * - pasuje po metodzie HTTP i ścieżce.
     */
    private boolean matches(RateLimiterProperties.Rule rule, RequestContext ctx) {
        return switch (rule.getType()) {
            /*
             * Reguła globalna obowiązuje każdy request.
             *
             * Przykład:
             * "cała aplikacja może przyjąć maksymalnie 100 000 requestów/min".
             */
            case GLOBAL -> true;

            /*
             * Reguła tenantowa obowiązuje tylko konkretnego tenanta.
             *
             * Jeśli request nie ma tenantId, reguła nie pasuje.
             */
            case TENANT -> equalsNullable(rule.getTenantId(), ctx.tenantId());

            /*
             * Reguła userowa obowiązuje tylko konkretnego użytkownika.
             *
             * Dobra dla wyjątków/override'ów per user.
             */
            case USER -> equalsNullable(rule.getUserId(), ctx.userId());

            /*
             * Reguła po API key używa hasha klucza, nie surowego API key.
             *
             * To jest ważne bezpieczeństwa: konfiguracja i Redis keys
             * nie powinny operować na plaintext API keys.
             */
            case API_KEY -> equalsNullable(rule.getApiKeyHash(), ctx.apiKeyHash());

            /*
             * Reguła planowa, np. FREE / PRO / ENTERPRISE.
             *
             * Porównanie jest case-insensitive, żeby "free" i "FREE"
             * nie były traktowane jako różne plany.
             */
            case PLAN -> equalsIgnoreCase(rule.getPlan(), ctx.plan());

            /*
             * Reguła endpointowa sprawdza metodę i path.
             *
             * Przykład:
             * POST /api/payments może mieć większy koszt albo ostrzejszy limit
             * niż GET /api/users.
             */
            case ENDPOINT -> methodMatches(rule, ctx) && pathMatches(rule, ctx);
        };
    }

    /**
     * Sprawdza dopasowanie metody HTTP dla reguły endpointowej.
     *
     * Jeśli rule.method == null, reguła pasuje do każdej metody.
     *
     * Przykład:
     * - method=null  -> pasuje do GET, POST, PUT itd.
     * - method=POST  -> pasuje tylko do POST.
     */
    private boolean methodMatches(RateLimiterProperties.Rule rule, RequestContext ctx) {
        return rule.getMethod() == null
                || rule.getMethod().equalsIgnoreCase(ctx.method());
    }

    /**
     * Sprawdza dopasowanie ścieżki requestu do wzorca reguły.
     *
     * Obsługiwane są trzy warianty:
     *
     * 1. Brak patternu:
     *    - reguła pasuje do każdej ścieżki.
     *
     * 2. Pattern zakończony "/**":
     *    - pasuje do całego poddrzewa ścieżek.
     *
     *    Przykład:
     *    /api/**
     *    pasuje do:
     *    - /api/users
     *    - /api/payments/123
     *    - /api/admin/reports
     *
     * 3. Pattern zakończony "/*":
     *    - w tej implementacji działa jako startsWith dla prefiksu.
     *
     *    Przykład:
     *    /api/*
     *    pasuje do:
     *    - /api/users
     *    - /api/payments
     *
     * 4. Dokładne dopasowanie:
     *    - pattern musi być identyczny jak ctx.path().
     *
     * Uwaga:
     * obecne "/*" nie ogranicza dopasowania tylko do jednego segmentu.
     * Technicznie działa podobnie do prefix match.
     */
    private boolean pathMatches(RateLimiterProperties.Rule rule, RequestContext ctx) {
        String pattern = rule.getPathPattern();

        /*
         * Brak wzorca oznacza, że reguła nie ogranicza się do konkretnej ścieżki.
         */
        if (pattern == null || pattern.isBlank()) {
            return true;
        }

        /*
         * Dopasowanie poddrzewa.
         *
         * /api/** -> prefix /api
         */
        if (pattern.endsWith("/**")) {
            return ctx.path().startsWith(pattern.substring(0, pattern.length() - 3));
        }

        /*
         * Uproszczone wildcard dopasowanie.
         *
         * /api/* -> prefix /api
         *
         * W tej wersji jest to proste startsWith,
         * a nie pełna semantyka "jeden segment".
         */
        if (pattern.endsWith("/*")) {
            return ctx.path().startsWith(pattern.substring(0, pattern.length() - 2));
        }

        /*
         * Standardowe dokładne dopasowanie ścieżki.
         */
        return pattern.equals(ctx.path());
    }

    /**
     * Bezpieczne porównanie wartości wymagających dokładnego matcha.
     *
     * Jeżeli expected albo actual jest null, zwracamy false.
     *
     * To celowe:
     * reguła TENANT/USER/API_KEY nie powinna pasować przypadkowo,
     * jeśli request nie ma odpowiedniej tożsamości.
     */
    private boolean equalsNullable(String expected, String actual) {
        return expected != null
                && actual != null
                && expected.equals(actual);
    }

    /**
     * Bezpieczne porównanie case-insensitive.
     *
     * Używane głównie dla planów, np. FREE / free / Free.
     */
    private boolean equalsIgnoreCase(String expected, String actual) {
        return expected != null
                && actual != null
                && expected.equalsIgnoreCase(actual);
    }
}