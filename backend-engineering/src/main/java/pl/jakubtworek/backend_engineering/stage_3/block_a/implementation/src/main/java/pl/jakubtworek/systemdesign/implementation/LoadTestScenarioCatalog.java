package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.src.main.java.pl.jakubtworek.systemdesign.implementation;

import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * Provides standard test scenarios for implementation and resilience validation.
 */
public final class LoadTestScenarioCatalog {

    private LoadTestScenarioCatalog() {
        // Utility class.
    }

    public static List<LoadTestScenario> defaultScenarios() {
        return List.of(
                new LoadTestScenario(
                        LoadTestType.BASELINE,
                        Duration.ofMinutes(20),
                        "Establish stable reference behavior.",
                        Set.of(TestMetric.P50_LATENCY, TestMetric.P95_LATENCY, TestMetric.P99_LATENCY,
                                TestMetric.CPU, TestMetric.DB_QPS, TestMetric.CACHE_HIT_RATIO,
                                TestMetric.DEPENDENCY_LATENCY),
                        "Metrics remain stable and define the baseline for future comparisons.",
                        "Without a baseline, later alerts and regressions are hard to interpret."
                ),
                new LoadTestScenario(
                        LoadTestType.STEP,
                        Duration.ofMinutes(45),
                        "Increase RPS gradually until tail latency grows non-linearly.",
                        Set.of(TestMetric.RPS, TestMetric.P95_LATENCY, TestMetric.P99_LATENCY,
                                TestMetric.CPU, TestMetric.POOL_WAIT, TestMetric.DB_LATENCY),
                        "The first latency knee matches the predicted bottleneck.",
                        "If a different metric saturates first, the capacity model is incomplete."
                ),
                new LoadTestScenario(
                        LoadTestType.SPIKE,
                        Duration.ofMinutes(10),
                        "Apply a sudden traffic spike to validate autoscaling, rate limits, and cache behavior.",
                        Set.of(TestMetric.RPS, TestMetric.RATE_LIMIT_429, TestMetric.P95_LATENCY,
                                TestMetric.CACHE_MISS_RATIO, TestMetric.DEPENDENCY_LATENCY),
                        "The system absorbs or rejects excess load without cascading failure.",
                        "Slow failures, uncontrolled retries, or dependency overload indicate weak guardrails."
                ),
                new LoadTestScenario(
                        LoadTestType.SOAK,
                        Duration.ofHours(4),
                        "Run sustained traffic to detect leaks, backlog growth, and latency drift.",
                        Set.of(TestMetric.MEMORY, TestMetric.GC_PAUSE, TestMetric.QUEUE_DEPTH,
                                TestMetric.OLDEST_MESSAGE_AGE, TestMetric.P95_LATENCY, TestMetric.RETRY_COUNT),
                        "No unbounded growth appears in memory, queues, or tail latency.",
                        "Gradual growth indicates leaks, unstable workers, or hidden retry amplification."
                ),
                new LoadTestScenario(
                        LoadTestType.CACHE_OFF,
                        Duration.ofMinutes(20),
                        "Validate database behavior when cache protection is removed.",
                        Set.of(TestMetric.DB_QPS, TestMetric.DB_LATENCY, TestMetric.POOL_WAIT,
                                TestMetric.ERROR_RATE, TestMetric.P95_LATENCY),
                        "Database limits are visible and do not surprise the team.",
                        "Immediate overload means the cache is the only protection layer."
                ),
                new LoadTestScenario(
                        LoadTestType.DEPENDENCY_FAILURE,
                        Duration.ofMinutes(15),
                        "Inject timeouts, 5xx, or high latency into remote dependencies.",
                        Set.of(TestMetric.TIMEOUT_COUNT, TestMetric.RETRY_COUNT, TestMetric.BREAKER_OPEN_RATE,
                                TestMetric.FALLBACK_COUNT, TestMetric.P95_LATENCY, TestMetric.ERROR_RATE),
                        "Timeouts, circuit breakers, and degradation mechanisms activate as designed.",
                        "If calls hang or retries grow unbounded, the dependency boundary is unsafe."
                ),
                new LoadTestScenario(
                        LoadTestType.RETRY_STORM,
                        Duration.ofMinutes(15),
                        "Validate that retries do not amplify transient failures excessively.",
                        Set.of(TestMetric.RETRY_COUNT, TestMetric.RETRY_AMPLIFICATION,
                                TestMetric.DEPENDENCY_LATENCY, TestMetric.BREAKER_OPEN_RATE),
                        "Retry amplification remains bounded and jitter spreads retry traffic.",
                        "High amplification means retries are happening at too many layers or without proper limits."
                )
        );
    }
}
