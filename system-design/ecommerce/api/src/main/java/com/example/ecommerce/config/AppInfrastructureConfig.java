package com.example.ecommerce.config;

import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableCaching
@EnableScheduling
@EnableRetry
@ConfigurationPropertiesScan(basePackages = "com.example.ecommerce")
public class AppInfrastructureConfig {
}
