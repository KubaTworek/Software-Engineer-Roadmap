package com.example.observability.server.metrics;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class MetricSemanticsTest {
    @Test void counterRateTreatsRestartAsResetInsteadOfNegativeTraffic() {
        var start = Instant.parse("2026-01-01T00:00:00Z");
        double rate = MetricSemantics.counterRate(List.of(
                new MetricSemantics.CounterPoint(start, 100),
                new MetricSemantics.CounterPoint(start.plusSeconds(10), 130),
                new MetricSemantics.CounterPoint(start.plusSeconds(20), 5)));
        assertThat(rate).isEqualTo(1.75);
    }

    @Test void histogramMergeAddsCountsAndSumButRequiresTheSameBuckets() {
        var first = new MetricSemantics.Histogram(List.of(.1, .5), List.of(2L, 5L, 5L), 5, 1.2);
        var second = new MetricSemantics.Histogram(List.of(.1, .5), List.of(3L, 7L, 7L), 7, 2.4);
        assertThat(MetricSemantics.merge(first, second).cumulativeCounts()).containsExactly(5L, 12L, 12L);
        assertThatThrownBy(() -> MetricSemantics.merge(first,
                new MetricSemantics.Histogram(List.of(.2), List.of(1L, 1L), 1, .1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void estimatesQuantileFromMergedDistributionInsteadOfAveragingPercentiles() {
        var histogram = new MetricSemantics.Histogram(
                List.of(.1, .5, 1.0), List.of(10L, 80L, 99L, 100L), 100, 30);
        assertThat(MetricSemantics.quantile(histogram, .95)).isBetween(.5, 1.0);
    }
}
