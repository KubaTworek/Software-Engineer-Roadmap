package com.example.urlshortener.config;

import com.example.urlshortener.analytics.AnalyticsProperties;
import com.example.urlshortener.admin.AdminProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({RateLimitProperties.class, AnalyticsProperties.class, AdminProperties.class})
public class PropertiesConfig {
}
