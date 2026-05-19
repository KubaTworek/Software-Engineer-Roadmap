package pl.jakubtworek.backend_systems_lab_stage_1.block_b.heap_size;

public final class HeapResizeExperiment {

    public static void main(String[] args) throws Exception {
        // This experiment demonstrates how heap resizing behavior
        // can influence GC predictability and application stability.
        //
        // The application creates:
        // - allocation pressure,
        // - temporary spikes,
        // - periods of reduced memory usage.
        //
        // The goal is NOT to produce an OutOfMemoryError.
        // The goal is to observe:
        // - heap expansion,
        // - heap shrinking,
        // - GC frequency,
        // - pause predictability,
        // - runtime behavior under different -Xms / -Xmx settings.
        //
        // Recommended comparisons:
        //
        // Case A:
        // -Xms256m -Xmx2g
        //
        // Case B:
        // -Xms2g -Xmx2g
        //
        // Observe GC logs and JFR behavior differences.

        System.out.println("Starting heap resize experiment");

        AllocationPhases phases = new AllocationPhases();

        phases.warmupPhase();
        phases.spikePhase();
        phases.cooldownPhase();
        phases.stableLoadPhase();

        System.out.println("Experiment finished");
    }
}