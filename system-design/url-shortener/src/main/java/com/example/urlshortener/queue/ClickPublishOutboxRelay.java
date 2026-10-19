package com.example.urlshortener.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Publishes claimed outbox rows and marks them sent only after RabbitMQ confirms the write. */
@Component
@ConditionalOnProperty(name = "app.queue.durable-publishing", havingValue = "true")
public class ClickPublishOutboxRelay {
    private final ClickPublishOutboxClaims claims;
    private final RabbitTemplate rabbit;
    private final QueueProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Duration confirmTimeout;
    private final int maxAttempts;

    public ClickPublishOutboxRelay(ClickPublishOutboxClaims claims, RabbitTemplate rabbit,
            QueueProperties properties, ObjectMapper objectMapper, Clock clock,
            @org.springframework.beans.factory.annotation.Value("${app.queue.publisher-confirm-timeout:PT5S}") Duration confirmTimeout,
            @org.springframework.beans.factory.annotation.Value("${app.queue.publish-outbox-max-attempts:8}") int maxAttempts) {
        this.claims = claims;
        this.rabbit = rabbit;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.confirmTimeout = confirmTimeout;
        this.maxAttempts = maxAttempts;
    }

    @Scheduled(fixedDelayString = "${app.queue.publish-outbox-delay:PT1S}")
    public void relay() {
        for (ClickPublishOutboxMessage row : claims.claim()) {
            try {
                ClickMessage message = objectMapper.readValue(row.getPayloadJson(), ClickMessage.class);
                CorrelationData correlation = new CorrelationData(row.getEventId());
                rabbit.convertAndSend(properties.clickExchange(), properties.clickRoutingKey(), message, correlation);
                CorrelationData.Confirm confirm = correlation.getFuture()
                        .get(confirmTimeout.toMillis(), TimeUnit.MILLISECONDS);
                if (!confirm.isAck()) {
                    throw new IllegalStateException("RabbitMQ rejected publish: " + confirm.getReason());
                }
                claims.published(row.getEventId());
            } catch (Exception failure) {
                if (row.getAttempts() >= maxAttempts) {
                    claims.deadLetter(row.getEventId(), failure.getMessage());
                } else {
                    long seconds = Math.min(300, 1L << Math.min(row.getAttempts(), 8));
                    claims.retry(row.getEventId(), Duration.ofSeconds(seconds), failure.getMessage());
                }
            }
        }
    }
}
