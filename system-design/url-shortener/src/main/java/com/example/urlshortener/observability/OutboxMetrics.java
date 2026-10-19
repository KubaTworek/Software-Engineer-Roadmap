package com.example.urlshortener.observability;

import com.example.urlshortener.outbox.UrlProjectionOutboxRepository;
import com.example.urlshortener.queue.ClickPublishOutboxRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/** Queue depth gauges expose saturation without event identifiers as tags. */
@Component
public class OutboxMetrics {
    public OutboxMetrics(MeterRegistry registry, UrlProjectionOutboxRepository projections,
            ClickPublishOutboxRepository clicks) {
        Gauge.builder("url_shortener.outbox.pending", projections,
                        repository -> repository.countBySentAtIsNullAndDeadLetteredAtIsNull())
                .tag("kind", "projection").register(registry);
        Gauge.builder("url_shortener.outbox.dead_letters", projections,
                        UrlProjectionOutboxRepository::countByDeadLetteredAtIsNotNull)
                .tag("kind", "projection").register(registry);
        Gauge.builder("url_shortener.outbox.pending", clicks, ClickPublishOutboxRepository::countByPublishedAtIsNull)
                .tag("kind", "click-publish").register(registry);
    }
}
