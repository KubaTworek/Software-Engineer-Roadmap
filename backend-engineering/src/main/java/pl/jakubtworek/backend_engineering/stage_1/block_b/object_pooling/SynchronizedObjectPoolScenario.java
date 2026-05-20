package pl.jakubtworek.backend_engineering.stage_1.block_b.object_pooling;

public final class SynchronizedObjectPoolScenario implements Scenario {

    private final PoolingConfig config;

    public SynchronizedObjectPoolScenario(PoolingConfig config) {
        this.config = config;
    }

    @Override
    public long run() throws InterruptedException {
        // This scenario uses one shared synchronized pool.
        //
        // It demonstrates that pooling can replace allocation pressure
        // with lock contention and reduced scalability.
        SynchronizedReusableBufferPool pool = new SynchronizedReusableBufferPool(
                config.poolSize(),
                config.payloadSizeBytes()
        );

        PoolWorker[] workers = new PoolWorker[config.workerThreads()];
        Thread[] threads = new Thread[config.workerThreads()];

        int iterationsPerWorker = config.iterations() / config.workerThreads();

        for (int i = 0; i < config.workerThreads(); i++) {
            workers[i] = new PoolWorker(pool, iterationsPerWorker, config.payloadSizeBytes());
            threads[i] = new Thread(workers[i], "pool-worker-" + i);
        }

        long start = System.nanoTime();

        for (Thread thread : threads) {
            thread.start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        long end = System.nanoTime();

        long checksum = 0;

        for (PoolWorker worker : workers) {
            checksum += worker.checksum();
        }

        System.out.println("Shared pool size after scenario: " + pool.size());
        System.out.println("Synchronized pool wall time ms: " + (end - start) / 1_000_000);

        return checksum;
    }
}