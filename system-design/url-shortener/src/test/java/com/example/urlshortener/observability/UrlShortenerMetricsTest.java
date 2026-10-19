package com.example.urlshortener.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class UrlShortenerMetricsTest {

    @Test
    void exposesOnlyBoundedTagsAndNeverAUserControlledShortCode() {
        var registry = new SimpleMeterRegistry();
        var metrics = new UrlShortenerMetrics(registry);

        metrics.cacheHit();
        metrics.cacheMiss();
        metrics.projectionFailure();
        metrics.analyticsPublishFailure();
        metrics.projectionLag(Duration.ofMillis(25));

        assertThat(registry.getMeters())
                .allSatisfy(meter -> assertThat(meter.getId().getTags())
                        .allSatisfy(tag -> assertThat(tag.getKey()).isIn("source", "result")));
        assertThat(registry.getMeters())
                .noneSatisfy(meter -> assertThat(meter.getId().getTags())
                        .anySatisfy(tag -> assertThat(tag.getKey()).isIn("shortCode", "url", "userId", "ip")));
    }
}
