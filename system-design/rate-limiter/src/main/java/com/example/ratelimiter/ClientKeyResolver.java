package com.example.ratelimiter;

import com.sun.net.httpserver.HttpExchange;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Optional;

/**
 * Resolves the rate-limit subject for a request.
 *
 * Priority for stage 1:
 * 1. X-User-Id header
 * 2. X-Api-Key header
 * 3. Remote IP address
 */
public final class ClientKeyResolver {
    public String resolve(HttpExchange exchange) {
        Optional<String> userId = firstHeader(exchange, "X-User-Id");
        if (userId.isPresent()) {
            return "user:" + userId.get();
        }

        Optional<String> apiKey = firstHeader(exchange, "X-Api-Key");
        if (apiKey.isPresent()) {
            return "api-key:" + apiKey.get();
        }

        InetSocketAddress remoteAddress = exchange.getRemoteAddress();
        if (remoteAddress != null && remoteAddress.getAddress() != null) {
            return "ip:" + remoteAddress.getAddress().getHostAddress();
        }

        return "anonymous";
    }

    private Optional<String> firstHeader(HttpExchange exchange, String name) {
        List<String> values = exchange.getRequestHeaders().get(name);
        if (values == null || values.isEmpty()) {
            return Optional.empty();
        }

        String value = values.get(0);
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(value.trim());
    }
}
