package pl.jakubtworek.backend_engineering.stage_3.block_a.metrics.src.main.java.com.example.systemdesign.metrics;

import java.time.Duration;
import java.util.List;

/**
 * Provides an example catalog of metrics and starting alert thresholds.
 *
 * The thresholds are deliberately described as starting points.
 * A production system should calibrate them using real baselines and SLOs.
 */
public final class MetricCatalog {

    private MetricCatalog() {
        // Utility class.
    }

    /**
     * API metrics focus on throughput, latency, errors, concurrency, CPU, memory, and GC.
     */
    public static List<MetricDefinition> apiMetrics() {
        return List.of(
                new MetricDefinition("rps", MetricCategory.API, "Incoming request throughput", "rate per second"),
                new MetricDefinition("latency.p50", MetricCategory.API, "Median request latency", "percentile"),
                new MetricDefinition("latency.p95", MetricCategory.API, "Tail latency affecting slower users", "percentile"),
                new MetricDefinition("latency.p99", MetricCategory.API, "Extreme tail latency", "percentile"),
                new MetricDefinition("http.5xx.rate", MetricCategory.API, "Server-side error rate", "rate or percentage"),
                new MetricDefinition("http.429.rate", MetricCategory.API, "Rejected traffic caused by throttling", "rate or percentage"),
                new MetricDefinition("in_flight_requests", MetricCategory.API, "Current request concurrency", "gauge"),
                new MetricDefinition("cpu.utilization", MetricCategory.API, "CPU saturation signal", "average over window"),
                new MetricDefinition("memory.usage", MetricCategory.API, "Memory pressure signal", "gauge"),
                new MetricDefinition("gc.pause", MetricCategory.API, "Garbage collection impact", "duration and count")
        );
    }

    /**
     * Database metrics focus on query rate, latency, locks, pools, lag, and I/O.
     */
    public static List<MetricDefinition> databaseMetrics() {
        return List.of(
                new MetricDefinition("db.qps.read", MetricCategory.DATABASE, "Read query throughput", "rate per second"),
                new MetricDefinition("db.qps.write", MetricCategory.DATABASE, "Write query throughput", "rate per second"),
                new MetricDefinition("db.slow_queries", MetricCategory.DATABASE, "Queries slower than expected baseline", "count or rate"),
                new MetricDefinition("db.lock_wait", MetricCategory.DATABASE, "Time spent waiting for locks", "duration"),
                new MetricDefinition("db.pool.usage", MetricCategory.DATABASE, "Database connection pool utilization", "percentage"),
                new MetricDefinition("db.pool.wait", MetricCategory.DATABASE, "Time spent waiting for a connection", "duration"),
                new MetricDefinition("db.replication_lag", MetricCategory.DATABASE, "Delay between primary and replica", "duration"),
                new MetricDefinition("db.iops", MetricCategory.DATABASE, "Storage I/O pressure", "operations per second"),
                new MetricDefinition("db.latency", MetricCategory.DATABASE, "Query latency under load", "percentile")
        );
    }

    /**
     * Redis metrics focus on cache effectiveness, memory pressure, latency, and clients.
     */
    public static List<MetricDefinition> redisMetrics() {
        return List.of(
                new MetricDefinition("redis.keyspace_hits", MetricCategory.REDIS, "Successful cache reads", "counter"),
                new MetricDefinition("redis.keyspace_misses", MetricCategory.REDIS, "Cache misses", "counter"),
                new MetricDefinition("redis.hit_ratio", MetricCategory.REDIS, "Cache effectiveness", "ratio"),
                new MetricDefinition("redis.evicted_keys", MetricCategory.REDIS, "Keys removed due to memory pressure", "counter"),
                new MetricDefinition("redis.expired_keys", MetricCategory.REDIS, "Keys removed due to TTL expiration", "counter"),
                new MetricDefinition("redis.used_memory", MetricCategory.REDIS, "Current memory usage", "gauge"),
                new MetricDefinition("redis.mem_not_counted_for_evict", MetricCategory.REDIS, "Memory excluded from eviction accounting", "gauge"),
                new MetricDefinition("redis.latency", MetricCategory.REDIS, "Cache response latency", "percentile"),
                new MetricDefinition("redis.clients", MetricCategory.REDIS, "Number of connected clients", "gauge")
        );
    }

    /**
     * Starting alert rules derived from common operational heuristics.
     */
    public static List<AlertRule> startingAlertRules() {
        MetricDefinition apiP95 = new MetricDefinition(
                "latency.p95",
                MetricCategory.API,
                "Tail latency affecting slower users",
                "percentile"
        );

        MetricDefinition api5xx = new MetricDefinition(
                "http.5xx.rate",
                MetricCategory.API,
                "Server-side error rate",
                "percentage"
        );

        MetricDefinition dbPoolUsage = new MetricDefinition(
                "db.pool.usage",
                MetricCategory.DATABASE,
                "Database connection pool utilization",
                "percentage"
        );

        MetricDefinition redisHitRatio = new MetricDefinition(
                "redis.hit_ratio",
                MetricCategory.REDIS,
                "Cache effectiveness",
                "ratio"
        );

        MetricDefinition breakerOpen = new MetricDefinition(
                "breaker.open.rate",
                MetricCategory.RESILIENCE,
                "Circuit breaker open transitions",
                "rate per minute"
        );

        MetricDefinition queueOldestAge = new MetricDefinition(
                "queue.oldest_message_age",
                MetricCategory.QUEUE,
                "Age of the oldest unprocessed message",
                "duration"
        );

        return List.of(
                new AlertRule(
                        apiP95,
                        "p95 > 2x baseline",
                        Duration.ofMinutes(5),
                        AlertSeverity.WARNING,
                        "The API latency curve may be bending and should be compared with saturation metrics."
                ),
                new AlertRule(
                        api5xx,
                        "5xx > 1%",
                        Duration.ofMinutes(5),
                        AlertSeverity.CRITICAL,
                        "The API is returning too many server-side failures."
                ),
                new AlertRule(
                        dbPoolUsage,
                        "pool utilization > 80%",
                        Duration.ofMinutes(5),
                        AlertSeverity.WARNING,
                        "The database pool is close to saturation; pool wait should be checked."
                ),
                new AlertRule(
                        redisHitRatio,
                        "hit ratio < 80% for cache-heavy endpoints",
                        Duration.ofMinutes(10),
                        AlertSeverity.WARNING,
                        "The cache is not protecting the source of truth as expected."
                ),
                new AlertRule(
                        breakerOpen,
                        "breaker opens several times per minute on a critical dependency",
                        Duration.ofMinutes(1),
                        AlertSeverity.CRITICAL,
                        "A critical dependency is unstable or overloaded."
                ),
                new AlertRule(
                        queueOldestAge,
                        "oldest message age exceeds business time budget",
                        Duration.ofMinutes(5),
                        AlertSeverity.CRITICAL,
                        "The queue backlog is violating the business processing window."
                )
        );
    }
}
