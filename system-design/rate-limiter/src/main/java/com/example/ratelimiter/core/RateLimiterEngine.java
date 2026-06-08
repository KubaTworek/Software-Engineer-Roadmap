package com.example.ratelimiter.core;

import com.example.ratelimiter.config.RateLimiterProperties;
import com.example.ratelimiter.quota.QuotaService;
import com.example.ratelimiter.usage.UsageEventPublisher;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * RateLimiterEngine to centralny komponent decyzyjny aplikacji.
 *
 * To tutaj request zostaje oceniony pod kątem wszystkich pasujących reguł.
 *
 * Przepływ wygląda tak:
 *
 * RequestContext
 *   -> RuleMatcher: znajdź pasujące reguły
 *   -> RedisTokenBucketLimiter: spróbuj skonsumować tokeny dla każdej reguły
 *   -> fallback, jeśli Redis nie działa
 *   -> RateLimitDecision: zbuduj decyzję końcową
 *   -> metryki, logi, quota, usage event
 *
 * Ważne:
 * request jest dozwolony tylko wtedy, gdy WSZYSTKIE wymagane reguły pozwalają.
 */
@Service
public class RateLimiterEngine {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterEngine.class);

    /**
     * Odpowiada za znalezienie reguł, które mają zastosowanie do danego requestu.
     *
     * Przykładowo dla requestu:
     * POST /api/payments, tenant=t1, user=u1, plan=FREE
     *
     * RuleMatcher może zwrócić kilka reguł naraz:
     * - globalną,
     * - tenantową,
     * - userową,
     * - endpointową,
     * - planową.
     */
    private final RuleMatcher ruleMatcher;

    /**
     * Główna implementacja rate limitingu oparta o Redis.
     *
     * RedisTokenBucketLimiter wykonuje atomowe zużycie tokenów,
     * zwykle przez Lua script.
     *
     * To jest preferowana ścieżka w normalnym działaniu aplikacji,
     * bo Redis jest współdzielony między instancjami serwisu.
     */
    private final RedisTokenBucketLimiter redisLimiter;

    /**
     * Awaryjny limiter lokalny.
     *
     * Jest używany wtedy, gdy Redis jest niedostępny,
     * a strategia awarii dla reguły to LOCAL_FALLBACK.
     *
     * Taki limiter działa tylko w pamięci konkretnej instancji aplikacji,
     * więc nie daje globalnej spójności, ale pozwala utrzymać częściową ochronę.
     */
    private final LocalFallbackLimiter localFallbackLimiter;

    /**
     * Konfiguracja Rate Limitera.
     *
     * Zawiera m.in. domyślną strategię awarii,
     * która jest używana, jeśli konkretna reguła nie ma własnej strategii.
     */
    private final RateLimiterProperties properties;

    /**
     * Publikuje usage event po każdej decyzji rate limitera.
     *
     * W Etapie 6 takie eventy mogą trafiać np. do Kafki
     * i służyć do analityki, audytu, dashboardów albo billingów.
     */
    private final UsageEventPublisher usageEventPublisher;

    /**
     * Obsługuje długoterminowe wykorzystanie quota.
     *
     * Quota to coś innego niż krótki rate limit.
     *
     * Rate limit:
     * - "100 requestów na minutę"
     *
     * Quota:
     * - "1 000 000 requestów miesięcznie dla tenanta"
     *
     * W tej klasie quota jest aktualizowana tylko dla zaakceptowanych requestów.
     */
    private final QuotaService quotaService;

    /**
     * Rejestr metryk Micrometer.
     *
     * Służy do wystawiania metryk np. dla Prometheusa:
     * - liczba decyzji allowed/denied,
     * - błędy Redisa,
     * - inne metryki techniczne.
     */
    private final MeterRegistry meterRegistry;

    public RateLimiterEngine(
            RuleMatcher ruleMatcher,
            RedisTokenBucketLimiter redisLimiter,
            LocalFallbackLimiter localFallbackLimiter,
            RateLimiterProperties properties,
            UsageEventPublisher usageEventPublisher,
            QuotaService quotaService,
            MeterRegistry meterRegistry
    ) {
        this.ruleMatcher = ruleMatcher;
        this.redisLimiter = redisLimiter;
        this.localFallbackLimiter = localFallbackLimiter;
        this.properties = properties;
        this.usageEventPublisher = usageEventPublisher;
        this.quotaService = quotaService;
        this.meterRegistry = meterRegistry;
    }

    /**
     * Główna metoda sprawdzająca, czy request może zostać przepuszczony.
     *
     * To jest najważniejszy flow:
     *
     * 1. Dopasuj reguły do requestu.
     * 2. Dla każdej reguły spróbuj skonsumować tokeny.
     * 3. Zbierz decyzje cząstkowe.
     * 4. Zbuduj decyzję końcową.
     * 5. Zapisz metryki.
     * 6. Jeśli request odrzucony, zaloguj blokadę.
     * 7. Jeśli request zaakceptowany, zapisz usage do quota.
     * 8. Opublikuj usage event.
     *
     * Finalna decyzja zależy od wszystkich dopasowanych reguł.
     * Jeśli chociaż jedna reguła odmówi, cały request powinien być odrzucony.
     */
    public RateLimitDecision check(RequestContext ctx) {
        /*
         * Najpierw znajdujemy wszystkie reguły pasujące do requestu.
         *
         * To jest moment, w którym aplikacja decyduje, jakie limity obowiązują:
         * - globalne,
         * - tenantowe,
         * - userowe,
         * - endpointowe,
         * - planowe,
         * - override'y.
         */
        List<RateLimiterProperties.Rule> rules = ruleMatcher.match(ctx);

        /*
         * Każda reguła daje własną decyzję cząstkową.
         *
         * Przykład:
         * - global_limit: allowed
         * - tenant_limit: allowed
         * - endpoint_payments_limit: denied
         *
         * W takim przypadku decyzja końcowa będzie denied.
         */
        List<RuleDecision> decisions = new ArrayList<>();

        for (RateLimiterProperties.Rule rule : rules) {
            decisions.add(consumeRule(rule, ctx));
        }

        /*
         * Agregujemy decyzje cząstkowe do jednej odpowiedzi.
         *
         * RateLimitDecision.from(...) powinno zwykle:
         * - ustawić allowed=false, jeśli dowolna reguła odmówiła,
         * - wybrać najbardziej restrykcyjne remaining,
         * - wybrać największy retryAfterSeconds z odrzuconych reguł,
         * - zachować listę szczegółowych decyzji per reguła.
         */
        RateLimitDecision decision = RateLimitDecision.from(decisions);

        /*
         * Metryka ogólna: ile decyzji było allowed/denied.
         *
         * To podstawowy sygnał operacyjny.
         * W Prometheus/Grafana pozwala zobaczyć np. nagły wzrost 429.
         */
        meterRegistry.counter(
                "rate_limiter.decisions",
                "decision", decision.allowed() ? "allowed" : "denied"
        ).increment();

        if (!decision.allowed()) {
            /*
             * Logujemy tylko requesty zablokowane.
             *
             * To jest ważne diagnostycznie:
             * - kto został zablokowany,
             * - dla jakiego tenanta,
             * - na jakiej ścieżce,
             * - przez jakie reguły.
             *
             * Uwaga: principalKey powinien być bezpieczny,
             * np. oparty o hash API key, a nie surowy sekret.
             */
            log.warn(
                    "rate_limit_denied principal={} tenant={} path={} method={} decisions={}",
                    ctx.principalKey(),
                    ctx.tenantId(),
                    ctx.path(),
                    ctx.method(),
                    decision.ruleDecisions()
            );
        } else {
            /*
             * Quota aktualizujemy tylko dla requestów zaakceptowanych.
             *
             * Jeśli request został odrzucony przez rate limiter,
             * nie powinien zużywać miesięcznej/długoterminowej quoty.
             *
             * totalCost to suma kosztów wszystkich dopasowanych reguł.
             * To jest uproszczenie projektowe — w bardziej dopracowanej wersji
             * warto bardzo jasno zdefiniować, czy quota ma liczyć:
             * - koszt endpointu,
             * - koszt najwyższej reguły,
             * - czy sumę kosztów reguł.
             */
            long totalCost = rules.stream()
                    .mapToLong(RateLimiterProperties.Rule::getCost)
                    .sum();

            quotaService.recordAcceptedUsage(ctx, Math.max(1, totalCost));
        }

        /*
         * Usage event publikujemy po każdej decyzji.
         *
         * Dzięki temu downstream, np. Kafka consumer, może analizować:
         * - zaakceptowane requesty,
         * - zablokowane requesty,
         * - tenantów generujących największy ruch,
         * - nadużycia,
         * - dane pod dashboardy.
         */
        usageEventPublisher.publish(ctx, decision);

        return decision;
    }

    /**
     * Próbuje skonsumować tokeny dla jednej konkretnej reguły.
     *
     * Normalna ścieżka:
     * - użyj Redisa,
     * - wykonaj atomowe consume,
     * - zwróć RuleDecision.
     *
     * Awaryjna ścieżka:
     * - Redis rzuca wyjątek,
     * - wybieramy strategię awarii,
     * - zwracamy decyzję zgodną z FAIL_OPEN / FAIL_CLOSED / LOCAL_FALLBACK.
     */
    private RuleDecision consumeRule(RateLimiterProperties.Rule rule, RequestContext ctx) {
        try {
            /*
             * Preferowana ścieżka produkcyjna.
             *
             * Redis zapewnia współdzielony stan między wieloma instancjami aplikacji.
             * Dzięki temu limit per user/tenant/API key działa globalnie,
             * a nie tylko lokalnie na jednej instancji.
             */
            return redisLimiter.consume(rule, ctx);

        } catch (Exception ex) {
            /*
             * Błąd Redisa nie może być ignorowany.
             *
             * Liczymy go jako metrykę per rule,
             * żeby szybko wykryć, które reguły mają problemy z backendem limitera.
             */
            meterRegistry.counter(
                    "rate_limiter.redis.errors",
                    "rule", rule.getId()
            ).increment();

            /*
             * Strategia awarii może być ustawiona per reguła.
             *
             * Jeśli reguła nie ma własnej strategii,
             * używamy globalnej domyślnej strategii z konfiguracji.
             */
            RateLimiterProperties.FailureStrategy strategy =
                    rule.getFailureStrategy() != null
                            ? rule.getFailureStrategy()
                            : properties.getDefaultFailureStrategy();

            log.warn(
                    "redis_rate_limit_failed rule={} strategy={} error={}",
                    rule.getId(),
                    strategy,
                    ex.toString()
            );

            /*
             * Zachowanie systemu przy awarii Redisa.
             *
             * FAIL_OPEN:
             * - przepuszczamy request,
             * - chronimy dostępność API,
             * - ale ryzykujemy brak ochrony przed nadużyciami.
             *
             * FAIL_CLOSED:
             * - blokujemy request,
             * - chronimy backend,
             * - ale awaria Redisa może wyglądać dla użytkownika jak awaria API.
             *
             * LOCAL_FALLBACK:
             * - używamy lokalnego limitera w pamięci instancji,
             * - zachowujemy częściową ochronę,
             * - ale tracimy globalną spójność między instancjami.
             */
            return switch (strategy) {
                case FAIL_OPEN -> new RuleDecision(
                        rule.getId(),
                        true,
                        rule.getCapacity(),
                        rule.getCapacity(),
                        0,
                        "fail-open",
                        "REDIS_UNAVAILABLE"
                );

                case FAIL_CLOSED -> new RuleDecision(
                        rule.getId(),
                        false,
                        rule.getCapacity(),
                        0,
                        30,
                        "fail-closed",
                        "REDIS_UNAVAILABLE"
                );

                case LOCAL_FALLBACK -> localFallbackLimiter.consume(
                        rule,
                        ctx,
                        "REDIS_UNAVAILABLE"
                );
            };
        }
    }
}