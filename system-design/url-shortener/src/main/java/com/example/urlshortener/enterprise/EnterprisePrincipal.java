package com.example.urlshortener.enterprise;

public record EnterprisePrincipal(
    Long id,
    String name,
    String tier,
    int rateLimitPerMinute
) {}
