package pl.jakubtworek.backend_engineering.stage_3.block_a.metrics;

import org.junit.jupiter.api.Test;
import pl.jakubtworek.backend_engineering.stage_3.block_a.metrics.AlertEvaluator;
import pl.jakubtworek.backend_engineering.stage_3.block_a.metrics.DefaultAlertRules;
import pl.jakubtworek.backend_engineering.stage_3.block_a.metrics.MetricSnapshot;
import pl.jakubtworek.backend_engineering.stage_3.block_a.metrics.cache.CacheImpactCalculator;
import pl.jakubtworek.backend_engineering.stage_3.block_a.metrics.cache.RedisCounters;
import pl.jakubtworek.backend_engineering.stage_3.block_a.metrics.cache.RedisHealthCalculator;
import pl.jakubtworek.backend_engineering.stage_3.block_a.metrics.capacity.BottleneckAnalyzer;
import pl.jakubtworek.backend_engineering.stage_3.block_a.metrics.capacity.BottleneckType;
import pl.jakubtworek.backend_engineering.stage_3.block_a.metrics.capacity.CapacityCalculator;
import pl.jakubtworek.backend_engineering.stage_3.block_a.metrics.capacity.CapacityInput;
import pl.jakubtworek.backend_engineering.stage_3.block_a.metrics.queue.QueueHealthEvaluator;
import pl.jakubtworek.backend_engineering.stage_3.block_a.metrics.queue.QueueMetrics;
import pl.jakubtworek.backend_engineering.stage_3.block_a.metrics.resilience.RetryCounters;
import pl.jakubtworek.backend_engineering.stage_3.block_a.metrics.resilience.RetryMetricsCalculator;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SystemDesignMetricsTest {

    @Test
    void redisRatioDoesNotOverflowForLargeCounters() {
        RedisCounters counters = new RedisCounters(
                Long.MAX_VALUE, Long.MAX_VALUE, 0, 0,
                90, 100, 0
        );

        assertThat(RedisHealthCalculator.hitRatio(counters)).isEqualTo(0.5);
        assertThat(RedisHealthCalculator.usedMemoryRatio(counters)).isEqualTo(0.9);
    }

    @Test
    void cacheImpactRejectsNonFiniteInputs() {
        assertThatThrownBy(() -> CacheImpactCalculator.databaseReadQpsAfterCache(
                Double.NaN, 0.2, 1
        )).isInstanceOf(IllegalArgumentException.class);
        assertThat(CacheImpactCalculator.savedDatabaseReadQps(1_000, 0.8, 2))
                .isEqualTo(1_600);
    }

    @Test
    void retryCountersRemainInternallyConsistent() {
        RetryCounters counters = new RetryCounters(100, 125, 10);

        assertThat(RetryMetricsCalculator.amplification(counters)).isEqualTo(1.25);
        assertThat(RetryMetricsCalculator.retrySuccessRatio(counters)).isEqualTo(0.4);
        assertThat(RetryMetricsCalculator.retryStormRisk(counters)).isTrue();

        assertThatThrownBy(() -> new RetryCounters(100, 110, 11))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void queueThresholdsRespectBusinessBudgetBoundary() {
        QueueHealthEvaluator evaluator = new QueueHealthEvaluator(Duration.ofMinutes(5));

        QueueMetrics atBoundary = new QueueMetrics(1, Duration.ofMinutes(5), 0, 0, 0.05);
        QueueMetrics unhealthy = new QueueMetrics(10, Duration.ofMinutes(6), 5, 1, 0.06);

        assertThat(evaluator.oldestMessageExceedsBudget(atBoundary)).isFalse();
        assertThat(evaluator.backlogGrowingWithoutHealthyWorkers(atBoundary)).isFalse();
        assertThat(evaluator.oldestMessageExceedsBudget(unhealthy)).isTrue();
        assertThat(evaluator.hasPersistentDlqProblem(unhealthy)).isTrue();
        assertThat(evaluator.backlogGrowingWithoutHealthyWorkers(unhealthy)).isTrue();
    }

    @Test
    void invalidMetricValuesCannotSilentlyDisableAlerts() {
        assertThatThrownBy(() -> new MetricSnapshot(Map.of("api.5xx_rate", Double.NaN)))
                .isInstanceOf(IllegalArgumentException.class);

        AlertEvaluator evaluator = new AlertEvaluator(DefaultAlertRules.apiRules());
        MetricSnapshot snapshot = new MetricSnapshot(Map.of(
                "api.p95_ms", 250.0,
                "api.baseline_p95_ms", 100.0,
                "api.5xx_rate", 0.02,
                "api.cpu_utilization", 0.90,
                "api.in_flight_requests", 120.0,
                "api.baseline_in_flight_requests", 50.0
        ));

        assertThat(evaluator.firingAlerts(snapshot)).hasSize(4);
    }

    @Test
    void capacityModelIgnoresResourcesUnusedByTheRequestPath() {
        CapacityInput input = new CapacityInput(
                1, 2, 0.5, 0.01,
                10, 0, 0.1,
                100, 0, 0,
                0.2, 1
        );

        assertThat(CapacityCalculator.dependencyPoolLimitRps(input)).isInfinite();
        assertThat(CapacityCalculator.dbWriteLimitRps(input)).isInfinite();
        assertThat(new BottleneckAnalyzer().firstBottleneck(input).type())
                .isEqualTo(BottleneckType.API_CPU);
        assertThatThrownBy(() -> CapacityCalculator.dbReadQpsAfterCache(Double.NaN, input))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
