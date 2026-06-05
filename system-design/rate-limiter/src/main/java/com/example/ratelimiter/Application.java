package com.example.ratelimiter;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;

public final class Application {
    public static void main(String[] args) throws IOException {
        int port = getIntEnv("SERVER_PORT", 8080);
        int maxRequests = getIntEnv("RATE_LIMIT_MAX_REQUESTS", 5);
        int windowSeconds = getIntEnv("RATE_LIMIT_WINDOW_SECONDS", 60);

        RateLimitConfig config = new RateLimitConfig(maxRequests, Duration.ofSeconds(windowSeconds));
        RateLimiter limiter = new FixedWindowRateLimiter(config);
        ClientKeyResolver keyResolver = new ClientKeyResolver();

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/health", Application::health);
        server.createContext("/api/hello", new RateLimitedHandler(limiter, keyResolver, Application::hello));
        server.createContext("/api/data", new RateLimitedHandler(limiter, keyResolver, Application::data));
        server.setExecutor(Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()));
        server.start();

        System.out.printf("Rate Limiter Stage 1 started on http://localhost:%d%n", port);
        System.out.printf("Limit: %d requests / %d seconds%n", maxRequests, windowSeconds);
        System.out.println("Protected endpoints: /api/hello, /api/data");
        System.out.println("Try using headers: X-User-Id or X-Api-Key");
    }

    private static void health(HttpExchange exchange) throws IOException {
        sendJson(exchange, 200, """
                {
                  "status": "UP",
                  "timestamp": "%s"
                }
                """.formatted(Instant.now()));
    }

    private static void hello(HttpExchange exchange) throws IOException {
        sendJson(exchange, 200, """
                {
                  "message": "Hello from protected endpoint",
                  "timestamp": "%s"
                }
                """.formatted(Instant.now()));
    }

    private static void data(HttpExchange exchange) throws IOException {
        sendJson(exchange, 200, """
                {
                  "items": ["alpha", "beta", "gamma"],
                  "timestamp": "%s"
                }
                """.formatted(Instant.now()));
    }

    private static void sendJson(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] response = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, response.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(response);
        }
    }

    private static int getIntEnv(String name, int defaultValue) {
        String rawValue = System.getenv(name);
        if (rawValue == null || rawValue.isBlank()) {
            return defaultValue;
        }

        try {
            return Integer.parseInt(rawValue.trim());
        } catch (NumberFormatException exception) {
            System.err.printf("Invalid value for %s=%s. Using default: %d%n", name, rawValue, defaultValue);
            return defaultValue;
        }
    }
}
