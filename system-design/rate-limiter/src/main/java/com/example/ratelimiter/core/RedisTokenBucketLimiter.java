package com.example.ratelimiter.core;

import com.example.ratelimiter.config.RateLimiterProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * RedisTokenBucketLimiter wykonuje właściwe zużycie tokenów w Redisie.
 *
 * To jest produkcyjna ścieżka rate limitingu:
 *
 * RateLimiterEngine
 *   -> RedisTokenBucketLimiter.consume(...)
 *   -> Lua script w Redisie
 *   -> RuleDecision
 *
 * Główna rola tej klasy:
 * - zbudować poprawny Redis key dla reguły i requestu,
 * - uruchomić atomowy skrypt Lua,
 * - przetłumaczyć wynik z Redisa na RuleDecision,
 * - wystawić metryki Redis latency i allowed/denied.
 *
 * Dzięki Redisowi stan limitów jest współdzielony między wieloma instancjami aplikacji.
 */
@Component
public class RedisTokenBucketLimiter {

    /**
     * Klient Redis używany do wykonywania skryptu Lua.
     *
     * StringRedisTemplate operuje na stringach,
     * co jest wystarczające dla kluczy i argumentów skryptu.
     */
    private final StringRedisTemplate redis;

    /**
     * Skrypt Lua implementujący atomowy Token Bucket.
     *
     * Atomowość jest kluczowa:
     * wiele requestów równocześnie może próbować zużyć tokeny
     * dla tego samego użytkownika/API key/tenanta.
     *
     * Gdybyśmy robili osobne operacje:
     * GET -> obliczenia -> SET
     *
     * mielibyśmy race condition.
     *
     * Lua wykonuje całą operację po stronie Redisa jako jedną jednostkę.
     */
    private final DefaultRedisScript<List> script;

    /**
     * Rejestr metryk.
     *
     * Używany do mierzenia:
     * - liczby decyzji Redis allowed/denied,
     * - latencji wykonania skryptu Redis.
     */
    private final MeterRegistry meterRegistry;

    /**
     * Główna konfiguracja Rate Limitera.
     *
     * Tutaj używana głównie do pobrania regionu,
     * który jest częścią Redis key.
     */
    private final RateLimiterProperties properties;

    public RedisTokenBucketLimiter(
            StringRedisTemplate redis,
            MeterRegistry meterRegistry,
            RateLimiterProperties properties
    ) throws Exception {
        this.redis = redis;
        this.meterRegistry = meterRegistry;
        this.properties = properties;

        /*
         * Skrypt token_bucket.lua jest ładowany z resources.
         *
         * Dzięki temu logika atomowego consume jest trzymana w osobnym pliku,
         * a nie jako długi string w Javie.
         */
        this.script = new DefaultRedisScript<>();
        this.script.setResultType(List.class);
        this.script.setScriptText(
                new String(
                        new ClassPathResource("token_bucket.lua")
                                .getInputStream()
                                .readAllBytes(),
                        StandardCharsets.UTF_8
                )
        );
    }

    /**
     * Próbuje skonsumować tokeny dla jednej reguły i jednego requestu.
     *
     * Ta metoda odpowiada za wykonanie pojedynczej decyzji cząstkowej.
     *
     * Przykład:
     * - reguła globalna -> jedna decyzja,
     * - reguła tenantowa -> druga decyzja,
     * - reguła endpointowa -> trzecia decyzja.
     *
     * RateLimiterEngine później agreguje te decyzje.
     */
    public RuleDecision consume(RateLimiterProperties.Rule rule, RequestContext ctx) {
        /*
         * Mierzymy czas wykonania operacji Redis.
         *
         * Redis jest na ścieżce krytycznej requestu,
         * więc jego latencja bezpośrednio wpływa na czas odpowiedzi API.
         */
        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            /*
             * Budujemy klucz Redisa reprezentujący bucket dla:
             * - konkretnej reguły,
             * - konkretnego principalu,
             * - konkretnego regionu.
             */
            String key = redisKey(rule, ctx);

            /*
             * TTL określa, jak długo bucket ma żyć w Redisie.
             *
             * Jeśli klient przestaje wysyłać requesty,
             * nie chcemy trzymać jego bucketu wiecznie.
             *
             * capacity / refillTokensPerSecond mówi, ile sekund potrzeba,
             * żeby pusty bucket odnowił się do pełna.
             *
             * Mnożymy przez 2 jako bezpieczny bufor.
             * Minimum 60 sekund zapobiega zbyt agresywnemu usuwaniu kluczy.
             */
            long ttlSeconds = Math.max(
                    60,
                    (long) Math.ceil(rule.getCapacity() / rule.getRefillTokensPerSecond()) * 2
            );

            /*
             * Wywołujemy atomowy skrypt Lua.
             *
             * KEYS:
             * - key: Redis key bucketu.
             *
             * ARGV:
             * - capacity,
             * - refillTokensPerSecond,
             * - cost,
             * - timestampMs,
             * - ttlSeconds.
             *
             * Skrypt powinien:
             * - odczytać aktualną liczbę tokenów,
             * - policzyć refill od ostatniego requestu,
             * - sprawdzić, czy wystarczy tokenów na koszt requestu,
             * - zaktualizować stan bucketu,
             * - ustawić TTL,
             * - zwrócić decyzję.
             */
            List<?> result = redis.execute(
                    script,
                    List.of(key),
                    String.valueOf(rule.getCapacity()),
                    String.valueOf(rule.getRefillTokensPerSecond()),
                    String.valueOf(rule.getCost()),
                    String.valueOf(ctx.timestampMs()),
                    String.valueOf(ttlSeconds)
            );

            /*
             * Oczekiwany wynik Lua:
             *
             * [0] allowed: 1 albo 0
             * [1] remaining tokens
             * [2] limit/capacity
             * [3] retryAfterSeconds
             *
             * Jeśli skrypt zwróci coś innego, traktujemy to jako błąd techniczny.
             * RateLimiterEngine obsłuży wyjątek zgodnie ze strategią awarii.
             */
            if (result == null || result.size() < 4) {
                throw new IllegalStateException("Invalid Redis Lua result");
            }

            boolean allowed = Long.parseLong(result.get(0).toString()) == 1L;
            long remaining = Long.parseLong(result.get(1).toString());
            long limit = Long.parseLong(result.get(2).toString());
            long retryAfter = Long.parseLong(result.get(3).toString());

            /*
             * Metryka decyzji z Redisa per reguła.
             *
             * Pozwala zobaczyć, które reguły najczęściej blokują requesty.
             */
            meterRegistry.counter(
                    "rate_limiter.redis.decisions",
                    "rule", rule.getId(),
                    "decision", allowed ? "allowed" : "denied"
            ).increment();

            /*
             * RuleDecision jest decyzją dla jednej reguły.
             *
             * Całościowa decyzja requestu powstaje później w RateLimiterEngine
             * przez agregację wielu RuleDecision.
             */
            return new RuleDecision(
                    rule.getId(),
                    allowed,
                    limit,
                    remaining,
                    retryAfter,
                    "redis",
                    allowed ? "OK" : "RATE_LIMIT_EXCEEDED"
            );

        } finally {
            /*
             * Zatrzymujemy timer niezależnie od wyniku.
             *
             * Dzięki finally mierzymy również przypadki,
             * w których Redis rzuci wyjątek.
             */
            sample.stop(
                    Timer.builder("rate_limiter.redis.latency")
                            .publishPercentileHistogram()
                            .register(meterRegistry)
            );
        }
    }

    /**
     * Buduje Redis key dla bucketu.
     *
     * Dobry key design jest krytyczny, bo od niego zależy:
     * - izolacja limitów,
     * - poprawność działania per user/API key/tenant,
     * - zachowanie w Redis Cluster,
     * - łatwość debugowania.
     */
    private String redisKey(RateLimiterProperties.Rule rule, RequestContext ctx) {
        /*
         * Principal określa, kogo limitujemy dla danej reguły.
         *
         * GLOBAL:
         * - jeden bucket dla całej aplikacji.
         *
         * TENANT:
         * - bucket per tenant.
         *
         * USER:
         * - bucket per user.
         *
         * API_KEY:
         * - bucket per hash API key.
         *
         * PLAN:
         * - bucket dla planu i konkretnego principalu.
         *   Dzięki temu plan FREE nie ma jednego wspólnego bucketu dla wszystkich,
         *   tylko limit jest per klient w ramach planu.
         *
         * ENDPOINT:
         * - bucket per endpoint i principal.
         *   Dzięki temu np. POST /api/exports może mieć osobny limit.
         */
        String principal = switch (rule.getType()) {
            case GLOBAL -> "global";
            case TENANT -> "tenant:" + ctx.tenantId();
            case USER -> "user:" + ctx.userId();
            case API_KEY -> "api-key:" + ctx.apiKeyHash();
            case PLAN -> "plan:" + ctx.plan() + ":" + ctx.principalKey();
            case ENDPOINT -> "endpoint:"
                    + rule.getMethod()
                    + ":"
                    + sanitize(rule.getPathPattern())
                    + ":"
                    + ctx.principalKey();
        };

        /*
         * Region jest częścią klucza.
         *
         * REGIONAL:
         * - limit liczony osobno w każdym regionie.
         *
         * GLOBAL_APPROXIMATE:
         * - używamy wspólnego oznaczenia "global-approx".
         *
         * W tym projekcie GLOBAL_APPROXIMATE jest uproszczeniem pod multi-region.
         */
        String regionPart =
                rule.getRegionMode() == RateLimiterProperties.RegionMode.REGIONAL
                        ? properties.getRegion()
                        : "global-approx";

        /*
         * Redis Cluster używa hash slotów.
         *
         * Fragment w nawiasach klamrowych, np. {user:123},
         * to hash tag. Redis Cluster używa go do wyboru slotu.
         *
         * Dzięki temu klucze dla tego samego principalu mogą trafić
         * do tego samego slotu, co ułatwia operacje wielokluczowe
         * w przyszłości.
         *
         * Finalny przykład:
         *
         * rl:{user:user-123}:eu-central-1:user-limit
         */
        return "rl:{"
                + sanitize(principal)
                + "}:"
                + regionPart
                + ":"
                + rule.getId();
    }

    /**
     * Czyści wartość używaną w Redis key.
     *
     * Redis pozwala na wiele znaków w kluczach, ale sanityzacja pomaga:
     * - utrzymać czytelne klucze,
     * - uniknąć spacji i znaków problematycznych,
     * - ograniczyć ryzyko przypadkowego popsucia formatu key.
     *
     * Jeśli wartość jest null, używamy "none".
     */
    private String sanitize(String value) {
        return value == null
                ? "none"
                : value.replaceAll("[^A-Za-z0-9:_-]", "_");
    }
}