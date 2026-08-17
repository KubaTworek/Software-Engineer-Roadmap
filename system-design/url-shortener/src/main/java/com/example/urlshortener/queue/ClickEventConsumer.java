package com.example.urlshortener.queue;

import com.example.urlshortener.analytics.AnalyticsService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class ClickEventConsumer {

    private final AnalyticsService analyticsService;

    public ClickEventConsumer(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @RabbitListener(queues = "${app.queue.click-queue:url-shortener.clicks.queue}")
    public void consume(ClickMessage message) {
        analyticsService.processClick(message);
    }
}
