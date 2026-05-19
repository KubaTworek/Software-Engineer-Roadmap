package pl.jakubtworek.backend_systems_lab_stage_1.block_b.thread_count;

public final class ExperimentRunner {

    private final ExperimentConfig config;
    private final Workload workload;

    public ExperimentRunner(ExperimentConfig config, Workload workload) {
        this.config = config;
        this.workload = workload;
    }

    public void run() throws InterruptedException {
        Worker[] workers = new Worker[config.threadCount()];
        Thread[] threads = new Thread[config.threadCount()];

        long endAtNanos = System.nanoTime() + config.durationSeconds() * 1_000_000_000L;

        for (int i = 0; i < config.threadCount(); i++) {
            workers[i] = new Worker(workload, endAtNanos);
            threads[i] = new Thread(workers[i], "worker-" + i);
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
        long checksum = 0;

        for (Worker worker : workers) {
            operations += worker.operations();
            checksum += worker.checksum();
        }

        double elapsedSeconds = (end - start) / 1_000_000_000.0;
        double opsPerSecond = operations / elapsedSeconds;

        System.out.println("Threads: " + config.threadCount());
        System.out.println("Mode: " + config.mode());
        System.out.println("Operations: " + operations);
        System.out.println("Ops/sec: " + (long) opsPerSecond);
        System.out.println("Checksum: " + checksum);
        System.out.println("Elapsed seconds: " + elapsedSeconds);
    }
}