package pl.jakubtworek.backend_engineering.stage_3.block_a.metrics.src.main.java.com.example.systemdesign.metrics;

import java.util.Comparator;
import java.util.List;

/**
 * Predicts the first bottleneck by taking the minimum capacity
 * across serial dependencies on the request path.
 */
public final class BottleneckAnalyzer {

    private BottleneckAnalyzer() {
        // Utility class.
    }

    public static List<BottleneckPrediction> predictAll(RequestPathProfile profile) {
        double apiLimit = CapacityFormula.apiCpuLimitRps(
                profile.replicas(),
                profile.vCpuPerReplica(),
                profile.targetCpuUtilization(),
                profile.cpuSecondsPerRequest()
        );

        double dependencyLimit = CapacityFormula.dependencyPoolLimitRps(
                profile.dependencyPoolSize(),
                profile.dependencyTrafficFraction(),
                profile.dependencyLatencySeconds()
        );

        double dbWriteLimit = CapacityFormula.databaseWriteLimitRps(
                profile.databaseWriteQpsLimit(),
                profile.writeRatio(),
                profile.writeQueriesPerRequest()
        );

        return List.of(
                new BottleneckPrediction(
                        BottleneckType.API_CPU,
                        apiLimit,
                        "API CPU becomes saturated when request CPU demand reaches the target utilization.",
                        "CPU utilization, run queue, p95 latency, and in-flight requests"
                ),
                new BottleneckPrediction(
                        BottleneckType.DEPENDENCY_POOL,
                        dependencyLimit,
                        "Dependency pool is saturated when required concurrency equals the pool size.",
                        "Pool utilization, pool wait time, dependency p95 latency, and timeouts"
                ),
                new BottleneckPrediction(
                        BottleneckType.DATABASE_WRITE,
                        dbWriteLimit,
                        "Database write path is saturated when write QPS exceeds the measured safe write limit.",
                        "Write QPS, write latency, lock wait, slow queries, and replication lag"
                )
        );
    }

    public static BottleneckPrediction predictFirst(RequestPathProfile profile) {
        return predictAll(profile).stream()
                .min(Comparator.comparingDouble(BottleneckPrediction::limitRps))
                .orElseThrow();
    }
}
