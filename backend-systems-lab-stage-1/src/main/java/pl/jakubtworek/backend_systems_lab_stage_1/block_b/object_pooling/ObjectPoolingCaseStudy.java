package pl.jakubtworek.backend_systems_lab_stage_1.block_b.object_pooling;

public final class ObjectPoolingCaseStudy {

    public static void main(String[] args) throws Exception {
        // This case study compares two strategies:
        //
        // 1. Allocate short-lived objects normally.
        // 2. Reuse objects through a simple pool.
        //
        // The goal is to show that object pooling is not automatically faster.
        // Pooling can:
        // - keep objects alive longer,
        // - move pressure from young generation to old generation,
        // - increase complexity,
        // - introduce synchronization or contention,
        // - prevent useful JVM optimizations.
        //
        // Run with GC logs and JFR to compare allocation behavior and GC impact.

        PoolingConfig config = PoolingConfig.fromArgs(args);

        System.out.println("Starting object pooling case study");
        System.out.println(config);

        runScenario("normal-allocation", new NormalAllocationScenario(config), config);
        runScenario("single-thread-pool", new SingleThreadObjectPoolScenario(config), config);
        runScenario("synchronized-pool", new SynchronizedObjectPoolScenario(config), config);

        System.out.println("Case study finished");
    }

    private static void runScenario(
            String name,
            Scenario scenario,
            PoolingConfig config
    ) throws InterruptedException {
        System.out.println("Starting scenario: " + name);

        long start = System.nanoTime();
        long checksum = scenario.run();
        long end = System.nanoTime();

        System.out.println("Scenario: " + name);
        System.out.println("Checksum: " + checksum);
        System.out.println("Elapsed ms: " + (end - start) / 1_000_000);
        System.out.println();

        // Give the JVM a short pause between scenarios.
        // This makes GC/JFR timelines easier to read.
        Thread.sleep(config.pauseBetweenScenariosMillis());
    }
}