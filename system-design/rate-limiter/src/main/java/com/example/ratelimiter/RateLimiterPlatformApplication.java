package com.example.ratelimiter;

import com.example.ratelimiter.config.RateLimiterProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(RateLimiterProperties.class)
public class RateLimiterPlatformApplication {
    public static void main(String[] args) {
        SpringApplication.run(RateLimiterPlatformApplication.class, args);
    }
}
