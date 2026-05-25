package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.src.main.java.pl.jakubtworek.systemdesign.concepts.capacity;

import java.util.Comparator;
import java.util.List;

/**
 * Finds the first bottleneck by comparing calculated limits.
 */
public class BottleneckAnalyzer {

    public List<Bottleneck> analyze(CapacityPlan plan) {
        return List.of(
                new Bottleneck(
                        BottleneckType.API_CPU,
                        CapacityCalculator.apiCpuLimitRps(plan),
                        "API CPU saturates when request CPU demand reaches target utilization.",
                        "api.cpu, api.p95, api.p99, api.in_flight_requests"
                ),
                new Bottleneck(
                        BottleneckType.DEPENDENCY_POOL,
                        CapacityCalculator.dependencyPoolLimitRps(plan),
                        "Dependency pool saturates when required concurrency equals pool size.",
                        "dependency.pool_usage, dependency.pool_wait, dependency.p95, timeout_count"
                ),
                new Bottleneck(
                        BottleneckType.DB_WRITE,
                        CapacityCalculator.dbWriteLimitRps(plan),
                        "Database write path saturates when write QPS exceeds measured safe write capacity.",
                        "db.write_qps, db.write_latency, db.lock_wait, db.slow_queries"
                )
        );
    }

    public Bottleneck first(CapacityPlan plan) {
        return analyze(plan).stream()
                .min(Comparator.comparingDouble(Bottleneck::limitRps))
                .orElseThrow();
    }
}