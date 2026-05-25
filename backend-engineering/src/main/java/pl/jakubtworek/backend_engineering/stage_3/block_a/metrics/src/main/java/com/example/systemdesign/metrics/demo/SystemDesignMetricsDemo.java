package pl.jakubtworek.backend_engineering.stage_3.block_a.metrics.src.main.java.com.example.systemdesign.metrics.demo;

import pl.jakubtworek.backend_engineering.stage_3.block_a.metrics.src.main.java.com.example.systemdesign.metrics.AlertEvaluator;
import pl.jakubtworek.backend_engineering.stage_3.block_a.metrics.src.main.java.com.example.systemdesign.metrics.AlertRule;
import pl.jakubtworek.backend_engineering.stage_3.block_a.metrics.src.main.java.com.example.systemdesign.metrics.DefaultAlertRules;
import pl.jakubtworek.backend_engineering.stage_3.block_a.metrics.src.main.java.com.example.systemdesign.metrics.MetricSnapshot;
import pl.jakubtworek.backend_engineering.stage_3.block_a.metrics.src.main.java.com.example.systemdesign.metrics.cache.CacheImpactCalculator;
import pl.jakubtworek.backend_engineering.stage_3.block_a.metrics.src.main.java.com.example.systemdesign.metrics.cache.RedisCounters;
import pl.jakubtworek.backend_engineering.stage_3.block_a.metrics.src.main.java.com.example.systemdesign.metrics.cache.RedisHealthCalculator;
import pl.jakubtworek.backend_engineering.stage_3.block_a.metrics.src.main.java.com.example.systemdesign.metrics.capacity.BottleneckAnalyzer;
import pl.jakubtworek.backend_engineering.stage_3.block_a.metrics.src.main.java.com.example.systemdesign.metrics.capacity.BottleneckResult;
import pl.jakubtworek.backend_engineering.stage_3.block_a.metrics.src.main.java.com.example.systemdesign.metrics.capacity.CapacityCalculator;
import pl.jakubtworek.backend_engineering.stage_3.block_a.metrics.src.main.java.com.example.systemdesign.metrics.capacity.CapacityInput;
import pl.jakubtworek.backend_engineering.stage_3.block_a.metrics.src.main.java.com.example.systemdesign.metrics.resiliance.RetryCounters;
import pl.jakubtworek.backend_engineering.stage_3.block_a.metrics.src.main.java.com.example.systemdesign.metrics.resiliance.RetryMetricsCalculator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * End-to-end demo showing the implementation-oriented usage:
 * - evaluates alerts,
 * - predicts bottleneck,
 * - calculates Redis health,
 * - calculates retry amplification,
 * - calculates cache impact on DB.
 */
public class SystemDesignMetricsDemo {

    public static void main(String[] args) {
        MetricSnapshot snapshot = new MetricSnapshot(Map.ofEntries(
                Map.entry("api.p95_ms", 420.0),
                Map.entry("api.baseline_p95_ms", 180.0),
                Map.entry("api.5xx_rate", 0.012),
                Map.entry("api.cpu_utilization", 0.86),
                Map.entry("api.in_flight_requests", 900.0),
                Map.entry("api.baseline_in_flight_requests", 300.0),

                Map.entry("db.pool_utilization", 0.84),
                Map.entry("db.pool_wait_ms", 60.0),
                Map.entry("db.slow_queries_per_minute", 50.0),
                Map.entry("db.baseline_slow_queries_per_minute", 15.0),

                Map.entry("redis.hit_ratio", 0.76),
                Map.entry("redis.evicted_keys_per_minute", 30.0),
                Map.entry("redis.used_memory_ratio", 0.92),

                Map.entry("dependency.timeout_rate", 0.015),
                Map.entry("retry.amplification", 1.35),
                Map.entry("breaker.opens_per_minute", 4.0)
        ));

        List<AlertRule> rules = new ArrayList<>();
        rules.addAll(DefaultAlertRules.apiRules());
        rules.addAll(DefaultAlertRules.databaseRules());
        rules.addAll(DefaultAlertRules.redisRules());
        rules.addAll(DefaultAlertRules.resilienceRules());

        AlertEvaluator alertEvaluator = new AlertEvaluator(rules);

        System.out.println("FIRING ALERTS:");
        alertEvaluator.firingAlerts(snapshot).forEach(System.out::println);

        CapacityInput capacityInput = new CapacityInput(
                4,
                2.0,
                0.70,
                0.003,
                30,
                0.10,
                0.4,
                1200,
                0.20,
                2,
                0.20,
                1
        );

        BottleneckAnalyzer analyzer = new BottleneckAnalyzer();
        BottleneckResult first = analyzer.firstBottleneck(capacityInput);

        System.out.println();
        System.out.println("FIRST BOTTLENECK:");
        System.out.println(first);

        double paymentConcurrencyAt750Rps = CapacityCalculator.concurrency(
                750 * 0.10,
                0.4
        );

        System.out.println("Payment concurrency at 750 RPS = " + paymentConcurrencyAt750Rps);

        RedisCounters redis = new RedisCounters(
                7600,
                2400,
                100,
                500,
                900_000_000,
                1_000_000_000,
                120_000_000
        );

        System.out.println();
        System.out.println("REDIS:");
        System.out.println("Hit ratio = " + RedisHealthCalculator.hitRatio(redis));
        System.out.println("Memory close to limit = " + RedisHealthCalculator.memoryCloseToLimit(redis));
        System.out.println("Eviction risk = " + RedisHealthCalculator.hasContinuousEvictions(redis));

        double dbReadQps = CacheImpactCalculator.databaseReadQpsAfterCache(
                1000,
                RedisHealthCalculator.missRatio(redis),
                2
        );

        System.out.println("DB read QPS after cache = " + dbReadQps);

        RetryCounters retryCounters = new RetryCounters(
                1000,
                1350,
                120
        );

        System.out.println();
        System.out.println("RETRY:");
        System.out.println("Amplification = " + RetryMetricsCalculator.amplification(retryCounters));
        System.out.println("Retry success ratio = " + RetryMetricsCalculator.retrySuccessRatio(retryCounters));
        System.out.println("Retry storm risk = " + RetryMetricsCalculator.retryStormRisk(retryCounters));
    }
}