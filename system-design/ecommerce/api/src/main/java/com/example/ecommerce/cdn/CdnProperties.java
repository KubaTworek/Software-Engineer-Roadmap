package com.example.ecommerce.cdn;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.cdn")
public record CdnProperties(
        boolean enabled,
        String baseUrl,
        String originBaseUrl,
        String version
) {
}
