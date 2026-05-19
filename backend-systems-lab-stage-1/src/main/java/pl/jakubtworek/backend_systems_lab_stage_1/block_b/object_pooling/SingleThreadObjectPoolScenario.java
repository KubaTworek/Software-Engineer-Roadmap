package pl.jakubtworek.backend_systems_lab_stage_1.block_b.object_pooling;

public final class SingleThreadObjectPoolScenario implements Scenario {

    private final PoolingConfig config;

    public SingleThreadObjectPoolScenario(PoolingConfig config) {
        this.config = config;
    }

    @Override
    public long run() {
        // This scenario uses a non-synchronized object pool.
        //
        // It avoids monitor contention, but it still keeps objects alive
        // and pays the cost of acquire/reset/release logic.
        ReusableBufferPool pool = new ReusableBufferPool(
                config.poolSize(),
                config.payloadSizeBytes()
        );

        long checksum = 0;

        for (int i = 0; i < config.iterations(); i++) {
            ReusableBuffer buffer = pool.acquire();

            buffer.write(i);
            checksum += buffer.checksum();

            pool.release(buffer);
        }

        System.out.println("Pool size after scenario: " + pool.size());

        return checksum;
    }
}