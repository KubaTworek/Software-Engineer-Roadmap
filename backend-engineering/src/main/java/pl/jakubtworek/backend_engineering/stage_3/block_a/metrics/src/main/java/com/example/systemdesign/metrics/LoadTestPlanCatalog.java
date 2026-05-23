package pl.jakubtworek.backend_engineering.stage_3.block_a.metrics.src.main.java.com.example.systemdesign.metrics;

import java.time.Duration;
import java.util.List;

/**
 * Provides common load test plans for scalability and resilience validation.
 */
public final class LoadTestPlanCatalog {

    private LoadTestPlanCatalog() {
        // Utility class.
    }

    public static List<LoadTestPlan> defaultPlans() {
        return List.of(
                new LoadTestPlan(
                        LoadTestKind.BASELINE,
                        Duration.ofMinutes(20),
                        "Establish normal latency, CPU, database, cache, and dependency behavior.",
                        List.of("p50", "p95", "p99", "CPU", "DB QPS", "cache hit ratio", "dependency latency"),
                        "The system remains stable and defines the baseline used by later alerts."
                ),
                new LoadTestPlan(
                        LoadTestKind.STEP,
                        Duration.ofMinutes(45),
                        "Increase RPS gradually until p95 or p99 latency grows non-linearly.",
                        List.of("RPS", "p95", "p99", "CPU", "pool wait", "DB latency", "in-flight requests"),
                        "The first knee of the latency curve should match the predicted bottleneck."
                ),
                new LoadTestPlan(
                        LoadTestKind.SPIKE,
                        Duration.ofMinutes(10),
                        "Apply a sudden traffic increase to test autoscaling, cold starts, rate limits, and cache behavior.",
                        List.of("RPS", "429", "cold start latency", "replica count", "cache misses", "dependency errors"),
                        "The system should absorb or reject excess load without cascading failure."
                ),
                new LoadTestPlan(
                        LoadTestKind.SOAK,
                        Duration.ofHours(4),
                        "Run sustained traffic to detect leaks, backlog growth, and latency drift.",
                        List.of("memory", "GC", "oldest message age", "queue depth", "p95", "p99"),
                        "No unbounded growth should appear in memory, queues, or tail latency."
                ),
                new LoadTestPlan(
                        LoadTestKind.CACHE_OFF,
                        Duration.ofMinutes(20),
                        "Validate database survivability when cache protection is removed.",
                        List.of("DB QPS", "DB latency", "slow queries", "CPU", "error rate"),
                        "The database limit should be visible and should not surprise the team."
                ),
                new LoadTestPlan(
                        LoadTestKind.DEPENDENCY_FAILURE,
                        Duration.ofMinutes(15),
                        "Inject timeouts, 5xx, or high latency into a dependency.",
                        List.of("timeouts", "retry count", "breaker state", "fallback count", "p95", "5xx"),
                        "Timeouts, circuit breaker, and graceful degradation should activate as designed."
                ),
                new LoadTestPlan(
                        LoadTestKind.RETRY_STORM,
                        Duration.ofMinutes(15),
                        "Validate that retries do not amplify a transient failure excessively.",
                        List.of("retry amplification", "dependency RPS", "jitter spread", "timeouts", "breaker state"),
                        "Retry amplification should remain bounded and should not occur at many layers."
                )
        );
    }
}
