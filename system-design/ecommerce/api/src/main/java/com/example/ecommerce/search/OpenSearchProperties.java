package com.example.ecommerce.search;
import org.springframework.boot.context.properties.ConfigurationProperties;
@ConfigurationProperties(prefix = "app.opensearch")
public record OpenSearchProperties(boolean enabled, String baseUrl, String username, String password, String productsIndex) {}
