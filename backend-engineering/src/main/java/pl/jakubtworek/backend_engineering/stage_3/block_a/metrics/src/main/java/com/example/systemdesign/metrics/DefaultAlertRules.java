package pl.jakubtworek.backend_engineering.stage_3.block_a.metrics.src.main.java.com.example.systemdesign.metrics;

import java.util.List;

/**
 * Factory for practical starting alert rules.
 *
 * These thresholds are starting heuristics.
 * They must be calibrated against real SLOs, baselines, and error budgets.
 */
public final class DefaultAlertRules {

    private DefaultAlertRules() {
    }

    public static List<AlertRule> apiRules() {
        return List.of(
                new AlertRule(
                        "API p95 latency above 2x baseline",
                        AlertSeverity.WARNING,
                        snapshot -> snapshot.get("api.p95_ms") > 2.0 * snapshot.get("api.baseline_p95_ms"),
                        "API p95 latency is above 2x baseline. Check CPU, in-flight requests, dependency latency, and DB pool wait.",
                        "API p95 latency is within baseline range."
                ),
                new AlertRule(
                        "API 5xx rate above 1 percent",
                        AlertSeverity.CRITICAL,
                        snapshot -> snapshot.get("api.5xx_rate") > 0.01,
                        "API 5xx rate is above 1%. This is likely user-visible server-side failure.",
                        "API 5xx rate is below the starting critical threshold."
                ),
                new AlertRule(
                        "CPU-bound API sustained CPU above 75 percent",
                        AlertSeverity.WARNING,
                        snapshot -> snapshot.get("api.cpu_utilization") > 0.75,
                        "API CPU utilization is high. For CPU-bound endpoints this may indicate approaching saturation.",
                        "API CPU utilization is below the starting warning threshold."
                ),
                new AlertRule(
                        "API CPU above 85 percent with growing in-flight requests",
                        AlertSeverity.CRITICAL,
                        snapshot -> snapshot.get("api.cpu_utilization") > 0.85
                                && snapshot.get("api.in_flight_requests") > snapshot.get("api.baseline_in_flight_requests"),
                        "CPU is very high and in-flight requests are growing. The API may be saturating.",
                        "CPU and in-flight request growth do not indicate critical API saturation."
                )
        );
    }

    public static List<AlertRule> databaseRules() {
        return List.of(
                new AlertRule(
                        "DB pool utilization above 80 percent",
                        AlertSeverity.WARNING,
                        snapshot -> snapshot.get("db.pool_utilization") > 0.80,
                        "Database pool utilization is above 80%. Check pool wait, DB latency, and query volume.",
                        "Database pool utilization is below the starting warning threshold."
                ),
                new AlertRule(
                        "DB pool wait above 50 ms",
                        AlertSeverity.WARNING,
                        snapshot -> snapshot.get("db.pool_wait_ms") > 50,
                        "Requests wait too long for DB connections. This often indicates pool or DB saturation.",
                        "Database pool wait is within the starting threshold."
                ),
                new AlertRule(
                        "DB slow queries above baseline",
                        AlertSeverity.WARNING,
                        snapshot -> snapshot.get("db.slow_queries_per_minute") > snapshot.get("db.baseline_slow_queries_per_minute"),
                        "Slow queries are above baseline. Check query plans, locks, indexes, and write pressure.",
                        "Slow queries are within baseline."
                )
        );
    }

    public static List<AlertRule> redisRules() {
        return List.of(
                new AlertRule(
                        "Redis hit ratio below 80 percent",
                        AlertSeverity.WARNING,
                        snapshot -> snapshot.get("redis.hit_ratio") < 0.80,
                        "Redis hit ratio is below 80% for cache-heavy traffic. DB load may increase.",
                        "Redis hit ratio is above the starting threshold."
                ),
                new AlertRule(
                        "Redis continuous evictions",
                        AlertSeverity.WARNING,
                        snapshot -> snapshot.get("redis.evicted_keys_per_minute") > 0,
                        "Redis is continuously evicting keys. Check maxmemory, working set, TTL, and eviction policy.",
                        "Redis does not show continuous evictions."
                ),
                new AlertRule(
                        "Redis memory close to maxmemory",
                        AlertSeverity.CRITICAL,
                        snapshot -> snapshot.get("redis.used_memory_ratio") > 0.90,
                        "Redis memory is close to maxmemory. Cache effectiveness and write behavior may degrade.",
                        "Redis memory usage is below the critical threshold."
                )
        );
    }

    public static List<AlertRule> resilienceRules() {
        return List.of(
                new AlertRule(
                        "Timeout rate above 1 percent",
                        AlertSeverity.WARNING,
                        snapshot -> snapshot.get("dependency.timeout_rate") > 0.01,
                        "Dependency timeout rate is above 1%. Check downstream latency and circuit breaker behavior.",
                        "Dependency timeout rate is below the starting threshold."
                ),
                new AlertRule(
                        "Retry amplification above 1.2x",
                        AlertSeverity.CRITICAL,
                        snapshot -> snapshot.get("retry.amplification") > 1.20,
                        "Retry amplification is above 1.2x. Retries may be increasing load during failure.",
                        "Retry amplification is within the starting threshold."
                ),
                new AlertRule(
                        "Circuit breaker opens repeatedly",
                        AlertSeverity.CRITICAL,
                        snapshot -> snapshot.get("breaker.opens_per_minute") > 3,
                        "Circuit breaker opens several times per minute on a critical dependency.",
                        "Circuit breaker open rate is below the critical threshold."
                )
        );
    }

    public static List<AlertRule> queueRules() {
        return List.of(
                new AlertRule(
                        "Oldest message age exceeds business budget",
                        AlertSeverity.CRITICAL,
                        snapshot -> snapshot.get("queue.oldest_message_age_seconds")
                                > snapshot.get("queue.business_budget_seconds"),
                        "Oldest queue message exceeds the business processing budget.",
                        "Queue oldest message age is within the business budget."
                ),
                new AlertRule(
                        "DLQ has persistent messages",
                        AlertSeverity.WARNING,
                        snapshot -> snapshot.get("queue.dlq_messages") > 0,
                        "Dead-letter queue contains messages. Investigate poison messages and worker failures.",
                        "Dead-letter queue is empty."
                )
        );
    }
}