package com.example.ratelimiter;

import com.example.ratelimiter.config.RateLimiterProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
@EnableConfigurationProperties(RateLimiterProperties.class)
@EnableScheduling
public class RateLimiterPlatformApplication {
    public static void main(String[] args) {
        SpringApplication.run(RateLimiterPlatformApplication.class, args);
    }
}
