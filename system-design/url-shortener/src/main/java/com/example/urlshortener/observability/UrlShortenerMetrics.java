package com.example.urlshortener.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * Low-cardinality operational signals. Short codes, URLs and client IDs are
 * deliberately excluded from tags because they would create an unbounded series.
 */
@Component
public class UrlShortenerMetrics {
    private final Counter cacheHits;
    private final Counter cacheMisses;
    private final Counter projectionFailures;
    private final Counter analyticsPublishFailures;
    private final Timer projectionLag;

    public UrlShortenerMetrics(MeterRegistry registry) {
        cacheHits = registry.counter("url_shortener.redirect.lookup", "source", "cache");
        cacheMisses = registry.counter("url_shortener.redirect.lookup", "source", "database");
        projectionFailures = registry.counter("url_shortener.projection.update", "result", "failure");
        analyticsPublishFailures = registry.counter("url_shortener.analytics.publish", "result", "failure");
        projectionLag = registry.timer("url_shortener.projection.propagation");
    }

    public void cacheHit() { cacheHits.increment(); }
    public void cacheMiss() { cacheMisses.increment(); }
    public void projectionFailure() { projectionFailures.increment(); }
    public void analyticsPublishFailure() { analyticsPublishFailures.increment(); }
    public void projectionLag(Duration duration) { projectionLag.record(duration); }
}
