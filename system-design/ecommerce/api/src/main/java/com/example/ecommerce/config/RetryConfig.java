package com.example.ecommerce.config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
@Configuration
public class RetryConfig {
    @Bean
    RetryTemplate integrationRetryTemplate(@Value("${app.integrations.retry.max-attempts:3}") int maxAttempts, @Value("${app.integrations.retry.initial-delay-ms:500}") long initialDelayMs, @Value("${app.integrations.retry.multiplier:2.0}") double multiplier) {
        RetryTemplate template = new RetryTemplate();
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(); retryPolicy.setMaxAttempts(maxAttempts);
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy(); backOffPolicy.setInitialInterval(initialDelayMs); backOffPolicy.setMultiplier(multiplier);
        template.setRetryPolicy(retryPolicy); template.setBackOffPolicy(backOffPolicy); return template;
    }
}
