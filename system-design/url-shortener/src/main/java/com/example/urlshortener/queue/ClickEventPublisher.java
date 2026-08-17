package com.example.urlshortener.queue;

import com.example.urlshortener.analytics.AnalyticsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class ClickEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(ClickEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final QueueProperties properties;
    private final AnalyticsService analyticsService;

    public ClickEventPublisher(RabbitTemplate rabbitTemplate, QueueProperties properties, AnalyticsService analyticsService) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
        this.analyticsService = analyticsService;
    }

    public void publish(ClickMessage message) {
        if (!properties.enabled()) {
            log.debug("Click queue disabled; processing click in-process eventId={} shortCode={}", message.eventId(), message.shortCode());
            analyticsService.processClick(message);
            return;
        }

        try {
            rabbitTemplate.convertAndSend(properties.clickExchange(), properties.clickRoutingKey(), message);
        } catch (AmqpException exception) {
            // Redirects must not fail because analytics queue is temporarily unavailable.
            log.warn("Failed to publish click event eventId={} shortCode={}", message.eventId(), message.shortCode(), exception);
        }
    }
}
