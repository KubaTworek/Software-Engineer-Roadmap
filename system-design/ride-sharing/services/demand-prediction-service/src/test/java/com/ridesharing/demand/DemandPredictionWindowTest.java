package com.ridesharing.demand;

import org.junit.jupiter.api.Test;
import java.time.*;

import static org.assertj.core.api.Assertions.assertThat;

class DemandPredictionWindowTest {
    @Test
    void oldTrafficExpiresInsteadOfInflatingForecastForever() {
        var clock = new MutableClock(Instant.parse("2026-01-01T10:00:00Z"));
        var service = new DemandPredictionService(clock);
        double baseline = service.forecast("warsaw", "cell-a", 15).expectedRequests();

        service.recordRideRequested("warsaw", "cell-a");
        service.recordRideRequested("warsaw", "cell-a");
        assertThat(service.forecast("warsaw", "cell-a", 15).expectedRequests()).isGreaterThan(baseline);

        clock.advance(Duration.ofMinutes(16));
        assertThat(service.forecast("warsaw", "cell-a", 15).expectedRequests()).isEqualTo(baseline);
    }

    private static final class MutableClock extends Clock {
        private Instant instant;
        private MutableClock(Instant instant) { this.instant = instant; }
        void advance(Duration duration) { instant = instant.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return instant; }
    }
}
