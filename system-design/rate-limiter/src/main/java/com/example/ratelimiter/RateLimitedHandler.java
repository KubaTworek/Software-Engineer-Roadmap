package com.example.ratelimiter;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * HTTP handler wrapper that applies rate limiting before calling the protected handler.
 */
public final class RateLimitedHandler implements HttpHandler {
    private final RateLimiter rateLimiter;
    private final ClientKeyResolver clientKeyResolver;
    private final HttpHandler protectedHandler;

    public RateLimitedHandler(
            RateLimiter rateLimiter,
            ClientKeyResolver clientKeyResolver,
            HttpHandler protectedHandler
    ) {
        this.rateLimiter = rateLimiter;
        this.clientKeyResolver = clientKeyResolver;
        this.protectedHandler = protectedHandler;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String clientKey = clientKeyResolver.resolve(exchange);
        RateLimitDecision decision = rateLimiter.check(clientKey);

        addRateLimitHeaders(exchange, decision);

        if (!decision.allowed()) {
            sendJson(exchange, 429, """
                    {
                      "error": "rate_limit_exceeded",
                      "message": "Too many requests. Please retry later.",
                      "retry_after_ms": %d
                    }
                    """.formatted(decision.retryAfterMillis()));
            return;
        }

        protectedHandler.handle(exchange);
    }

    private void addRateLimitHeaders(HttpExchange exchange, RateLimitDecision decision) {
        exchange.getResponseHeaders().add("X-RateLimit-Limit", String.valueOf(decision.limit()));
        exchange.getResponseHeaders().add("X-RateLimit-Remaining", String.valueOf(decision.remaining()));
        exchange.getResponseHeaders().add("X-RateLimit-Reset", String.valueOf(decision.resetEpochMillis() / 1000));

        if (!decision.allowed()) {
            long retryAfterSeconds = Math.max(1, (long) Math.ceil(decision.retryAfterMillis() / 1000.0));
            exchange.getResponseHeaders().add("Retry-After", String.valueOf(retryAfterSeconds));
        }
    }

    private void sendJson(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] response = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, response.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(response);
        }
    }
}
