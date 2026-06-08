package com.example.ratelimiter.api;

import com.example.ratelimiter.config.RateLimiterProperties;
import com.example.ratelimiter.core.RateLimitDecision;
import com.example.ratelimiter.core.RateLimiterEngine;
import com.example.ratelimiter.core.RequestContext;
import com.example.ratelimiter.security.ApiKeyHasher;
import com.example.ratelimiter.security.ClientIpResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * RateLimitFilter jest głównym punktem egzekwowania limitów dla requestów HTTP.
 *
 * To właśnie tutaj request wpada zanim trafi do kontrolera biznesowego,
 * np. DemoApiController.
 *
 * Przepływ wygląda tak:
 *
 * request HTTP
 *   -> RateLimitFilter
 *   -> zbudowanie RequestContext
 *   -> RateLimiterEngine.check(...)
 *   -> allowed: request idzie dalej
 *   -> denied: zwracamy 429 i kończymy request
 *
 * Ta klasa nie zawiera samego algorytmu Token Bucket.
 * Ona tylko zbiera dane z requestu, wywołuje engine i na podstawie decyzji
 * przepuszcza albo blokuje request.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    /**
     * Główna logika rate limitingu.
     *
     * Engine:
     * - dopasowuje reguły do requestu,
     * - sprawdza limity globalne, tenantowe, userowe, endpointowe i planowe,
     * - komunikuje się z Redisem albo fallback limiterem,
     * - zwraca finalną decyzję allow/deny.
     */
    private final RateLimiterEngine engine;

    /**
     * Odpowiada za ustalenie prawdziwego IP klienta.
     *
     * To ważne, bo aplikacja często stoi za proxy, load balancerem albo gatewayem.
     * Wtedy request.getRemoteAddr() może zwrócić IP proxy, a nie klienta.
     *
     * ClientIpResolver powinien poprawnie obsługiwać X-Forwarded-For
     * oraz trusted proxies.
     */
    private final ClientIpResolver clientIpResolver;

    /**
     * Hashuje API key przed użyciem go w rate limiterze.
     *
     * Nie chcemy trzymać ani logować surowych API keys.
     * Dlatego do RequestContext trafia hash, np. SHA-256,
     * a nie oryginalny sekret klienta.
     */
    private final ApiKeyHasher apiKeyHasher;

    /**
     * Konfiguracja aplikacji Rate Limitera.
     *
     * Z niej pobieramy m.in. nazwy nagłówków:
     * - który header zawiera API key,
     * - który header zawiera userId,
     * - który header zawiera tenantId,
     * - który header zawiera plan klienta.
     *
     * Dzięki temu nazwy headerów nie są zaszyte na sztywno w kodzie.
     */
    private final RateLimiterProperties properties;

    public RateLimitFilter(
            RateLimiterEngine engine,
            ClientIpResolver clientIpResolver,
            ApiKeyHasher apiKeyHasher,
            RateLimiterProperties properties
    ) {
        this.engine = engine;
        this.clientIpResolver = clientIpResolver;
        this.apiKeyHasher = apiKeyHasher;
        this.properties = properties;
    }

    /**
     * Określa, które ścieżki mają zostać pominięte przez Rate Limiter.
     *
     * Nie limitujemy tutaj:
     * - /actuator       -> healthchecki, metryki, Prometheus,
     * - /admin          -> zarządzanie regułami i debugowanie,
     * - /swagger-ui     -> dokumentacja API,
     * - /v3/api-docs    -> OpenAPI docs.
     *
     * To praktyczne, bo np. Prometheus musi regularnie pobierać metryki,
     * a admin musi mieć możliwość debugowania limitów nawet wtedy,
     * gdy normalne endpointy są ograniczane.
     *
     * Uwaga produkcyjna:
     * /admin nie powinno być publiczne. Powinno być zabezpieczone auth,
     * siecią prywatną albo osobnym internal gatewayem.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();

        return path.startsWith("/actuator")
                || path.startsWith("/admin")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs");
    }

    /**
     * Główna metoda filtra wykonywana dla każdego requestu, który nie został pominięty.
     *
     * Tutaj dzieją się cztery kluczowe rzeczy:
     *
     * 1. Budujemy RequestContext.
     * 2. Pytamy RateLimiterEngine o decyzję.
     * 3. Ustawiamy nagłówki informujące klienta o limicie.
     * 4. Przepuszczamy request dalej albo zwracamy 429.
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        /*
         * RequestContext to ustandaryzowany opis requestu dla Rate Limitera.
         *
         * Zbieramy tu wszystkie dane potrzebne do dopasowania reguł:
         * - metoda HTTP,
         * - path,
         * - IP klienta,
         * - hash API key,
         * - userId,
         * - tenantId,
         * - plan klienta,
         * - aktualny timestamp.
         *
         * Dzięki temu RateLimiterEngine nie musi znać szczegółów Servlet API.
         * Dostaje czysty model domenowy i na nim pracuje.
         */
        RequestContext ctx = new RequestContext(
                request.getMethod(),
                request.getRequestURI(),
                clientIpResolver.resolve(request),
                apiKeyHasher.sha256(request.getHeader(properties.getSecurity().getApiKeyHeader())),
                request.getHeader(properties.getSecurity().getUserHeader()),
                request.getHeader(properties.getSecurity().getTenantHeader()),
                defaultPlan(request.getHeader(properties.getSecurity().getPlanHeader())),
                System.currentTimeMillis()
        );

        /*
         * W tym miejscu zapada właściwa decyzja rate limitera.
         *
         * engine.check(ctx) sprawdza wszystkie reguły pasujące do requestu.
         * Request może podlegać wielu limitom jednocześnie, np.:
         * - globalnemu,
         * - tenantowemu,
         * - userowemu,
         * - endpointowemu,
         * - planowemu.
         *
         * Finalna decyzja jest pozytywna tylko wtedy,
         * gdy wszystkie wymagane reguły pozwalają na request.
         */
        RateLimitDecision decision = engine.check(ctx);

        /*
         * Nagłówki rate limit są ustawiane również dla requestów dozwolonych.
         *
         * Dzięki temu klient widzi:
         * - jaki limit obowiązuje,
         * - ile jeszcze requestów/tokenów zostało,
         * - ile reguł zostało użytych do podjęcia decyzji.
         *
         * Przy wielu regułach decision.limit() i decision.remaining()
         * zwykle powinny reprezentować najbardziej restrykcyjny limit,
         * czyli ten, który jest najbliżej zablokowania requestu.
         */
        response.setHeader("X-RateLimit-Limit", String.valueOf(decision.limit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(decision.remaining()));
        response.setHeader("X-RateLimit-Rule-Count", String.valueOf(decision.ruleDecisions().size()));

        /*
         * Jeśli RateLimiterEngine odrzucił request,
         * kończymy przetwarzanie tutaj.
         *
         * Nie wywołujemy filterChain.doFilter(...),
         * więc request nie trafi do kontrolera biznesowego.
         *
         * Zwracamy HTTP 429 Too Many Requests oraz Retry-After,
         * żeby klient wiedział, po ilu sekundach może spróbować ponownie.
         */
        if (!decision.allowed()) {
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
            response.setContentType("application/json");

            response.getWriter().write(
                    "{\"error\":\"rate_limit_exceeded\",\"retryAfterSeconds\":"
                            + decision.retryAfterSeconds()
                            + "}"
            );

            return;
        }

        /*
         * Jeśli request mieści się w limitach, przepuszczamy go dalej.
         *
         * Dopiero teraz request trafi do właściwego endpointu,
         * np. GET /api/users albo POST /api/payments.
         */
        filterChain.doFilter(request, response);
    }

    /**
     * Ustala domyślny plan klienta.
     *
     * Jeśli request nie zawiera nagłówka z planem,
     * traktujemy klienta jako FREE.
     *
     * To bezpieczne domyślne zachowanie:
     * brak informacji o planie nie powinien oznaczać wyższego limitu.
     */
    private String defaultPlan(String plan) {
        return plan == null || plan.isBlank() ? "FREE" : plan;
    }
}