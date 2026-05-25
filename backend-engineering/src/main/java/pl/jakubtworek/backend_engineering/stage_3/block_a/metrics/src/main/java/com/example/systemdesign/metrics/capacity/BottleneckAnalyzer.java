package pl.jakubtworek.backend_engineering.stage_3.block_a.metrics.src.main.java.com.example.systemdesign.metrics.capacity;

import java.util.Comparator;
import java.util.List;

/**
 * Finds the first expected bottleneck by comparing calculated limits.
 *
 * The first bottleneck is the component with the lowest safe RPS.
 */
public class BottleneckAnalyzer {

    public List<BottleneckResult> analyzeAll(CapacityInput input) {
        return List.of(
                new BottleneckResult(
                        BottleneckType.API_CPU,
                        CapacityCalculator.apiCpuLimitRps(input),
                        "API CPU saturates when request CPU demand reaches the target CPU utilization.",
                        "api.cpu_utilization, api.in_flight_requests, api.p95_ms, api.p99_ms"
                ),
                new BottleneckResult(
                        BottleneckType.DEPENDENCY_POOL,
                        CapacityCalculator.dependencyPoolLimitRps(input),
                        "Dependency pool saturates when required concurrency equals configured pool size.",
                        "dependency.pool_usage, dependency.pool_wait_ms, dependency.p95_ms, timeout_count"
                ),
                new BottleneckResult(
                        BottleneckType.DB_WRITE,
                        CapacityCalculator.dbWriteLimitRps(input),
                        "DB write path saturates when write QPS exceeds measured safe DB write capacity.",
                        "db.write_qps, db.write_latency_ms, db.lock_wait_ms, db.slow_queries"
                )
        );
    }

    public BottleneckResult firstBottleneck(CapacityInput input) {
        return analyzeAll(input).stream()
                .min(Comparator.comparingDouble(BottleneckResult::limitRps))
                .orElseThrow();
    }
}