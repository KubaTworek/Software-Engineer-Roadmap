package pl.jakubtworek.backend_systems_lab_stage_1.block_b.object_pooling;

public final class NormalAllocationScenario implements Scenario {

    private final PoolingConfig config;

    public NormalAllocationScenario(PoolingConfig config) {
        this.config = config;
    }

    @Override
    public long run() {
        // This scenario allocates fresh short-lived objects.
        //
        // In modern JVMs, short-lived allocation is often cheap:
        // - allocation is usually a simple pointer bump,
        // - objects die young,
        // - young generation collection is optimized for this pattern,
        // - escape analysis may eliminate some allocations in real code.
        long checksum = 0;

        for (int i = 0; i < config.iterations(); i++) {
            ReusableBuffer buffer = new ReusableBuffer(config.payloadSizeBytes());
            buffer.write(i);
            checksum += buffer.checksum();
        }

        return checksum;
    }
}