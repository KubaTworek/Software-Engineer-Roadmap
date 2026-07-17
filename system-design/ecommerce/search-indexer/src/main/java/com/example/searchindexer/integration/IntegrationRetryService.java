package com.example.searchindexer.integration;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import java.util.concurrent.Callable;

@Service
public class IntegrationRetryService {
    private static final Logger log = LoggerFactory.getLogger(IntegrationRetryService.class);
    private final RetryTemplate retryTemplate;
    private final Counter retries;

    public IntegrationRetryService(RetryTemplate retryTemplate, MeterRegistry registry) {
        this.retryTemplate = retryTemplate;
        this.retries = Counter.builder("search_indexer_integration_retries_total").register(registry);
    }

    public <T> T call(String name, Callable<T> callable) {
        return retryTemplate.execute(context -> {
            if (context.getRetryCount() > 0) {
                retries.increment();
                log.warn("Retrying integration={} attempt={}", name, context.getRetryCount() + 1);
            }
            return callable.call();
        });
    }

    public void run(String name, RetryableRunnable runnable) {
        call(name, () -> {
            runnable.run();
            return null;
        });
    }

    @FunctionalInterface
    public interface RetryableRunnable {
        void run() throws Exception;
    }
}
