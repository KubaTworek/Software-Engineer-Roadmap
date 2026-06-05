package pl.jakubtworek.backend.gateway.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

/**
 * Servlet filter odpowiedzialny za rate limiting na poziomie API Gateway.
 *
 * Rate limiting jest wykonywany przed przekazaniem requestu do downstream services,
 * dzięki czemu chronimy catalog-service, reservation-service i order-service przed
 * nadmiernym ruchem już na brzegu systemu.
 *
 * Ten filtr działa w modelu:
 *
 * - użytkownik z X-API-Key dostaje bucket per API key,
 * - użytkownik bez X-API-Key dostaje bucket per IP,
 * - limit jest liczony w Redisie, więc działa poprawnie przy wielu instancjach gatewaya,
 * - Redis outage nie blokuje ruchu, bo filtr działa w trybie fail-open.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    /**
     * Konfiguracja limitów ładowana z application.yml / zmiennych środowiskowych.
     *
     * Przykładowo:
     *
     * - RATE_LIMIT_ENABLED
     * - RATE_LIMIT_ANON_CAPACITY
     * - RATE_LIMIT_API_KEY_CAPACITY
     * - RATE_LIMIT_API_KEY_REFILL_TOKENS
     * - RATE_LIMIT_API_KEY_REFILL_PERIOD
     */
    private final RateLimitProperties properties;

    /**
     * Implementacja algorytmu token bucket oparta o Redis.
     *
     * Redis jest tutaj celowy: gdy API Gateway ma wiele replik, lokalny licznik w pamięci
     * każdej instancji dawałby niepoprawny globalny limit. Redis zapewnia współdzielony stan.
     */
    private final RedisTokenBucketRateLimiter rateLimiter;

    public RateLimitFilter(RateLimitProperties properties, RedisTokenBucketRateLimiter rateLimiter) {
        this.properties = properties;
        this.rateLimiter = rateLimiter;
    }

    /**
     * Decyduje, czy filtr powinien zostać pominięty dla danego requestu.
     *
     * Pomijamy:
     *
     * - cały filtr, jeśli rate limiting jest wyłączony,
     * - endpointy techniczne /actuator,
     * - prosty /health.
     *
     * To jest ważne, bo monitoring, health-checki ALB/ECS i Prometheus nie powinny być
     * przypadkowo blokowane przez rate limiter. Gdyby health-check dostał 429, orchestrator
     * mógłby błędnie uznać instancję za niezdrową.
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.enabled()) {
            return true;
        }

        String path = request.getRequestURI();

        return path.startsWith("/actuator") || path.equals("/health");
    }

    /**
     * Główna logika filtra.
     *
     * Dla każdego requestu:
     *
     * 1. Ustala tożsamość klienta.
     * 2. Dobiera odpowiedni bucket.
     * 3. Buduje klucz w Redisie.
     * 4. Próbuje zużyć token.
     * 5. Jeśli token jest dostępny, przepuszcza request dalej.
     * 6. Jeśli tokena nie ma, zwraca 429 Too Many Requests.
     * 7. Jeśli Redis nie działa, przechodzi w tryb fail-open.
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        /*
         * API key jest preferowaną tożsamością klienta.
         *
         * Jeśli klient poda X-API-Key, limitujemy po API key.
         * Jeśli nie poda, limitujemy po IP.
         *
         * Dzięki temu partner API może dostać wyższy limit niż anonimowy użytkownik.
         */
        String apiKey = Optional.ofNullable(request.getHeader("X-API-Key"))
                .filter(v -> !v.isBlank())
                .orElse(null);

        String identity = apiKey != null
                ? "api-key:" + apiKey
                : "ip:" + clientIp(request);

        /*
         * Dobór konfiguracji bucketa:
         *
         * - API key -> limit dla klientów uwierzytelnionych / partnerskich,
         * - IP      -> limit dla ruchu anonimowego.
         */
        RateLimitProperties.Bucket bucket = apiKey != null
                ? properties.apiKey()
                : properties.anonymous();

        /*
         * Klucz Redis zawiera:
         *
         * - tożsamość klienta,
         * - metodę HTTP,
         * - ścieżkę requestu.
         *
         * To oznacza, że limity są osobne np. dla:
         *
         * GET /events
         * POST /reservations
         * POST /orders
         *
         * To jest świadoma decyzja. Chroni szczególnie drogie endpointy write-side,
         * nie ograniczając ich tym samym licznikiem co tanie endpointy read-side.
         */
        String redisKey = "rate-limit:" + identity + ":" + request.getMethod() + ":" + request.getRequestURI();

        try {
            /*
             * Próba zużycia jednego tokena z bucketa.
             *
             * Wynik mówi:
             *
             * - czy request jest dozwolony,
             * - ile tokenów zostało,
             * - po ilu sekundach klient może spróbować ponownie.
             */
            TokenBucketDecision decision = rateLimiter.consume(redisKey, bucket);

            /*
             * Informacyjny header dla klienta.
             *
             * Przydatny w testach k6, debugowaniu i dla klientów API, którzy chcą
             * samodzielnie ograniczać tempo wysyłania requestów.
             */
            response.setHeader("X-RateLimit-Remaining", String.valueOf(decision.remainingTokens()));

            /*
             * Jeśli bucket nie ma już tokenów, gateway kończy request tutaj.
             *
             * Downstream services nie widzą tego requestu, więc nie zużywają CPU,
             * połączeń do bazy ani innych zasobów.
             */
            if (!decision.allowed()) {
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setHeader("Retry-After", String.valueOf(decision.retryAfterSeconds()));
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"rate_limit_exceeded\",\"message\":\"Too many requests\"}");
                return;
            }
        } catch (DataAccessException exception) {
            /*
             * Fail-open jest świadomą decyzją degradacyjną.
             *
             * Jeżeli Redis jest niedostępny, mamy dwie możliwe strategie:
             *
             * 1. Fail-closed:
             *    Blokujemy cały ruch, bo nie umiemy sprawdzić limitu.
             *
             * 2. Fail-open:
             *    Przepuszczamy ruch dalej, ale oznaczamy odpowiedź jako zdegradowaną.
             *
             * W tym projekcie wybieramy fail-open, bo awaria Redisa nie powinna zatrzymać
             * całego systemu zamówień. Trade-off: podczas awarii Redisa system jest mniej
             * chroniony przed nadmiernym ruchem.
             */
            log.warn("Rate limiter Redis unavailable. Falling back to fail-open mode.", exception);

            /*
             * Header diagnostyczny widoczny dla klienta i w testach.
             *
             * Dzięki niemu można potwierdzić, że rate limiter działał w trybie awaryjnym.
             */
            response.setHeader("X-RateLimit-Degraded", "true");
        }

        /*
         * Request przeszedł rate limiting albo rate limiter zdegradował się w trybie fail-open.
         * Przekazujemy request dalej do kolejnych filtrów / kontrolerów.
         */
        filterChain.doFilter(request, response);
    }

    /**
     * Ustala IP klienta.
     *
     * Jeśli aplikacja stoi za proxy/load balancerem, prawdziwy adres klienta zwykle znajduje się
     * w X-Forwarded-For. Wartość tego nagłówka może zawierać listę adresów:
     *
     * client, proxy1, proxy2
     *
     * Pierwszy adres jest najczęściej adresem oryginalnego klienta.
     *
     * Uwaga produkcyjna:
     * X-Forwarded-For można sfałszować, jeśli aplikacja jest dostępna bezpośrednio z internetu.
     * W produkcji należy ufać temu nagłówkowi tylko wtedy, gdy request przyszedł z zaufanego
     * load balancera / reverse proxy.
     */
    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");

        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }

        return request.getRemoteAddr();
    }
}

/**
 * Konfiguracja rejestracji filtra w servlet containerze.
 *
 * Samo oznaczenie RateLimitFilter jako @Component tworzy bean, ale FilterRegistrationBean
 * pozwala jawnie ustawić kolejność filtra w łańcuchu.
 */
@Configuration
class RateLimitFilterConfig {

    /**
     * Rejestruje RateLimitFilter z konkretną kolejnością.
     *
     * Order = 3 oznacza, że filtr powinien wykonać się wcześnie, zanim request trafi do
     * kontrolerów proxy. To ma sens, bo request przekraczający limit powinien zostać odrzucony
     * przed wykonaniem jakiejkolwiek pracy downstream.
     *
     * W większym systemie trzeba świadomie ustawić kolejność względem filtrów takich jak:
     *
     * - correlation ID,
     * - request logging,
     * - security/authentication,
     * - tracing.
     */
    @Bean
    FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(RateLimitFilter filter) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>();

        registration.setFilter(filter);
        registration.setOrder(3);

        return registration;
    }
}