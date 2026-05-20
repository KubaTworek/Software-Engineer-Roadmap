package pl.jakubtworek.backend_engineering.stage_1.block_b.object_pooling;

public final class PoolWorker implements Runnable {

    private final SynchronizedReusableBufferPool pool;
    private final int iterations;
    private final int payloadSizeBytes;

    private long checksum;

    public PoolWorker(
            SynchronizedReusableBufferPool pool,
            int iterations,
            int payloadSizeBytes
    ) {
        this.pool = pool;
        this.iterations = iterations;
        this.payloadSizeBytes = payloadSizeBytes;
    }

    @Override
    public void run() {
        // Each worker repeatedly acquires and releases objects from the same shared pool.
        //
        // This can create monitor contention.
        // In JFR, look for Java Monitor Blocked events around pool methods.
        for (int i = 0; i < iterations; i++) {
            ReusableBuffer buffer = pool.acquire();

            buffer.write(i + payloadSizeBytes);
            checksum += buffer.checksum();

            pool.release(buffer);
        }
    }

    public long checksum() {
        return checksum;
    }
}