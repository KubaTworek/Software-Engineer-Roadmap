package pl.jakubtworek.backend_engineering.stage_1.block_b.lock_contention;

public final class LockContentionCaseStudy {

    public static void main(String[] args) throws Exception {
        // This demo compares three shared counter strategies:
        // - synchronized monitor,
        // - AtomicLong with CAS,
        // - LongAdder with striped counters.
        //
        // The goal is to observe scalability under contention using JFR.
        //
        // Run with:
        // java -XX:StartFlightRecording=filename=lock-contention.jfr,duration=60s,settings=profile LockContentionCaseStudy

        ExperimentConfig config = ExperimentConfig.fromArgs(args);

        System.out.println("Starting lock contention case study");
        System.out.println(config);

        runScenario("synchronized", new SynchronizedCounter(), config);
        runScenario("atomic", new AtomicCounter(), config);
        runScenario("long-adder", new LongAdderCounter(), config);

        System.out.println("Case study finished");
    }

    private static void runScenario(
            String name,
            Counter counter,
            ExperimentConfig config
    ) throws InterruptedException {

        System.out.println("Starting scenario: " + name);

        CounterWorker[] workers = new CounterWorker[config.threadCount()];
        Thread[] threads = new Thread[config.threadCount()];

        long endAtNanos = System.nanoTime() + config.durationSecondsPerScenario() * 1_000_000_000L;

        for (int i = 0; i < config.threadCount(); i++) {
            workers[i] = new CounterWorker(counter, endAtNanos);
            threads[i] = new Thread(workers[i], name + "-worker-" + i);
        }

        long start = System.nanoTime();

        for (Thread thread : threads) {
            thread.start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        long end = System.nanoTime();

        long operations = 0;

        for (CounterWorker worker : workers) {
            operations += worker.operations();
        }

        System.out.println("Scenario: " + name);
        System.out.println("Counter value: " + counter.value());
        System.out.println("Operations: " + operations);
        System.out.println("Elapsed ms: " + (end - start) / 1_000_000);
        System.out.println("Ops/sec: " + operationsPerSecond(operations, end - start));
        System.out.println();
    }

    private static long operationsPerSecond(long operations, long elapsedNanos) {
        return (long) (operations / (elapsedNanos / 1_000_000_000.0));
    }
}