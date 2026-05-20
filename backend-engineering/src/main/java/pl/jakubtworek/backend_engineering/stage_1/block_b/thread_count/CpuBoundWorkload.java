package pl.jakubtworek.backend_engineering.stage_1.block_b.thread_count;

public final class CpuBoundWorkload implements Workload {

    private final int iterations;

    public CpuBoundWorkload(int iterations) {
        this.iterations = iterations;
    }

    @Override
    public long runOneOperation() {
        // This workload consumes CPU.
        //
        // Adding more threads than available CPU cores usually does not help much.
        // It can even reduce throughput because of scheduling overhead.
        return PrimeCounter.countPrimes(iterations);
    }
}