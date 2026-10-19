package com.example.urlshortener.queue;

import com.example.urlshortener.analytics.AnalyticsService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

@Component
public class ClickEventConsumer {

    private final AnalyticsService analyticsService;
    private final ObservationRegistry observations;

    public ClickEventConsumer(AnalyticsService analyticsService, ObservationRegistry observations) {
        this.analyticsService = analyticsService;
        this.observations = observations;
    }

    @RabbitListener(queues = "${app.queue.click-queue:url-shortener.clicks.queue}")
    public void consume(ClickMessage message) {
        Observation.createNotStarted("url.analytics.consume", observations)
                .lowCardinalityKeyValue("messaging.system", "rabbitmq")
                .observe(() -> analyticsService.processClick(message));
    }
}
