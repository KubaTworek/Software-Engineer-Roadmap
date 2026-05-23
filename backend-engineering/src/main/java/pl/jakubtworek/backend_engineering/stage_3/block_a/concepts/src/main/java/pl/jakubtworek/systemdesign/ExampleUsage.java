package pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.src.main.java.pl.jakubtworek.systemdesign;

import java.time.Duration;
import java.util.List;

/**
 * Small example showing how the conceptual classes can be used together.
 */
public class ExampleUsage {

    public static void main(String[] args) {
        double cpuRps = CapacityModel.cpuLimitedThroughput(
                8,
                0.70,
                0.020
        );

        double dbPoolRps = CapacityModel.poolLimitedThroughput(
                50,
                0.100
        );

        CapacityReport apiReport = new CapacityReport(
                "API",
                "maxRps = cpuCores * targetUtilization / cpuSecondsPerRequest",
                "CPU seconds per request",
                cpuRps,
                "CPU utilization and p95 latency",
                "API becomes CPU-bound when CPU approaches target utilization"
        );

        CapacityReport databaseReport = new CapacityReport(
                "Database connection pool",
                "maxRps = poolSize / dependencyLatencySeconds",
                "Average database latency",
                dbPoolRps,
                "Pool wait time and DB p95 latency",
                "Pool wait grows when all connections are busy"
        );

        AutoscalingConfig autoscaling = new AutoscalingConfig(
                2,
                20,
                ScalingMetric.REQUEST_CONCURRENCY,
                80
        );

        LoadTestScenario stepTest = new LoadTestScenario(
                LoadTestType.STEP,
                Duration.ofMinutes(30),
                500,
                List.of("p95 latency", "p99 latency", "CPU", "DB QPS", "pool wait", "cache hit ratio"),
                "p95 latency bends when the first saturated component is reached"
        );

        System.out.println(apiReport);
        System.out.println(databaseReport);
        System.out.println(autoscaling);
        System.out.println(stepTest);
    }
}
