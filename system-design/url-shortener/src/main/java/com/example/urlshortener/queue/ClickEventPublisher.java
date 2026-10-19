package com.example.urlshortener.queue;

import com.example.urlshortener.analytics.AnalyticsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import com.example.urlshortener.observability.UrlShortenerMetrics;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

@Service
public class ClickEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(ClickEventPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final QueueProperties properties;
    private final AnalyticsService analyticsService;
    private final UrlShortenerMetrics metrics;
    private final DurableClickPublisher durablePublisher;
    private final ObservationRegistry observations;

    public ClickEventPublisher(RabbitTemplate rabbitTemplate, QueueProperties properties, AnalyticsService analyticsService,
            UrlShortenerMetrics metrics, DurableClickPublisher durablePublisher, ObservationRegistry observations) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
        this.analyticsService = analyticsService;
        this.metrics = metrics;
        this.durablePublisher = durablePublisher;
        this.observations = observations;
    }

    public void publish(ClickMessage message) {
        if (!properties.enabled()) {
            log.debug("Click queue disabled; processing click in-process eventId={} shortCode={}", message.eventId(), message.shortCode());
            analyticsService.processClick(message);
            return;
        }

        if (properties.durablePublishing()) {
            durablePublisher.enqueue(message);
            return;
        }

        try {
            Observation.createNotStarted("url.analytics.publish", observations)
                    .lowCardinalityKeyValue("messaging.system", "rabbitmq")
                    .observe(() -> rabbitTemplate.convertAndSend(properties.clickExchange(), properties.clickRoutingKey(), message));
        } catch (AmqpException exception) {
            // Redirects must not fail because analytics queue is temporarily unavailable.
            log.warn("Failed to publish click event eventId={} shortCode={}", message.eventId(), message.shortCode(), exception);
            metrics.analyticsPublishFailure();
        }
    }
}
