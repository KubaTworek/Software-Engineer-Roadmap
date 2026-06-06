package com.example.urlshortener.queue;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.queue")
public record QueueProperties(
    boolean enabled,
    String clickExchange,
    String clickRoutingKey,
    String clickQueue,
    String clickDeadLetterExchange,
    String clickDeadLetterQueue
) {
    public QueueProperties {
        clickExchange = defaultIfBlank(clickExchange, "url-shortener.clicks.exchange");
        clickRoutingKey = defaultIfBlank(clickRoutingKey, "click.created");
        clickQueue = defaultIfBlank(clickQueue, "url-shortener.clicks.queue");
        clickDeadLetterExchange = defaultIfBlank(clickDeadLetterExchange, "url-shortener.clicks.dlx");
        clickDeadLetterQueue = defaultIfBlank(clickDeadLetterQueue, "url-shortener.clicks.dlq");
    }

    private static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
