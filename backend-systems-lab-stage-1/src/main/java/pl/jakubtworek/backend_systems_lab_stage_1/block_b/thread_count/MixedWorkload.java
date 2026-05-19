package pl.jakubtworek.backend_systems_lab_stage_1.block_b.thread_count;

public final class MixedWorkload implements Workload {

    private final int cpuIterations;
    private final int waitMillis;

    public MixedWorkload(int cpuIterations, int waitMillis) {
        this.cpuIterations = cpuIterations;
        this.waitMillis = waitMillis;
    }

    @Override
    public long runOneOperation() {
        // This workload combines CPU work and waiting.
        //
        // Real backend services often look like this:
        // - parse / validate / serialize data,
        // - call database or remote service,
        // - do some more CPU work.
        long result = PrimeCounter.countPrimes(cpuIterations);

        try {
            Thread.sleep(waitMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return result;
    }
}