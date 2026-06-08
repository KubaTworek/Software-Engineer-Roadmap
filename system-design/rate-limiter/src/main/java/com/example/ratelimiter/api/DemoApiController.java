package com.example.ratelimiter.api;

import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * DemoApiController wystawia przykładowe endpointy biznesowe,
 * na których testujemy działanie Rate Limitera.
 *
 * Ta klasa sama NIE implementuje logiki limitowania.
 * Rate limiting powinien działać wcześniej, np. w filtrze HTTP,
 * interceptorze albo osobnym gatewayu.
 *
 * Innymi słowy:
 *
 * request -> RateLimiterFilter -> DemoApiController
 *
 * Jeżeli request przekroczy limit, nie powinien w ogóle dojść
 * do metod z tej klasy. Wtedy aplikacja zwróci 429 Too Many Requests.
 *
 * Jeżeli request przejdzie przez limiter, dopiero wtedy wykonywana jest
 * jedna z poniższych metod.
 */
@RestController
@RequestMapping("/api")
public class DemoApiController {

    /**
     * Lekki endpoint testowy.
     *
     * W konfiguracji Rate Limitera ten endpoint powinien mieć niski koszt,
     * np. cost = 1.
     *
     * Przykładowe zastosowanie:
     * - lista użytkowników,
     * - prosty odczyt danych,
     * - tani request typu read-only.
     *
     * Dzięki temu możemy sprawdzić, że tanie operacje zużywają mniej tokenów
     * z bucketu niż operacje cięższe, np. płatności albo eksport danych.
     */
    @GetMapping("/users")
    public Map<String, Object> users() {
        return Map.of(
                "endpoint", "GET /api/users",
                "cost", 1,
                "timestamp", Instant.now().toString()
        );
    }

    /**
     * Średnio kosztowny endpoint testowy.
     *
     * W konfiguracji Rate Limitera ten endpoint powinien mieć większy koszt,
     * np. cost = 5.
     *
     * Przykładowe zastosowanie:
     * - wykonanie płatności,
     * - operacja zapisująca dane,
     * - request, który jest bardziej wrażliwy biznesowo niż zwykły GET.
     *
     * To pokazuje mechanizm ważony:
     * jeden request do /api/payments może zużyć tyle tokenów,
     * co kilka requestów do /api/users.
     */
    @PostMapping("/payments")
    public Map<String, Object> payments() {
        return Map.of(
                "endpoint", "POST /api/payments",
                "cost", 5,
                "paymentId", UUID.randomUUID().toString()
        );
    }

    /**
     * Ciężki endpoint testowy.
     *
     * W konfiguracji Rate Limitera ten endpoint powinien mieć bardzo wysoki koszt,
     * np. cost = 50.
     *
     * Przykładowe zastosowanie:
     * - eksport danych,
     * - generowanie raportu,
     * - kosztowna operacja asynchroniczna,
     * - endpoint mocno obciążający bazę lub storage.
     *
     * Ten endpoint służy do sprawdzenia, czy Rate Limiter potrafi ograniczać
     * nie tylko liczbę requestów, ale też "wagę" requestów.
     *
     * Praktyczny efekt:
     * użytkownik może wykonać dużo lekkich requestów do /api/users,
     * mniej requestów do /api/payments,
     * i bardzo mało requestów do /api/exports.
     */
    @PostMapping("/exports")
    public Map<String, Object> exports() {
        return Map.of(
                "endpoint", "POST /api/exports",
                "cost", 50,
                "exportId", UUID.randomUUID().toString()
        );
    }
}