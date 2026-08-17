package com.example.ratelimiter.core;

public record RequestContext(
        String method,
        String path,
        String clientIp,
        String apiKeyHash,
        String userId,
        String tenantId,
        String plan,
        long timestampMs
) {
    public String principalKey() {
        if (userId != null && !userId.isBlank()) return "user:" + userId;
        if (apiKeyHash != null && !apiKeyHash.isBlank()) return "api-key:" + apiKeyHash;
        return "ip:" + clientIp;
    }
}
