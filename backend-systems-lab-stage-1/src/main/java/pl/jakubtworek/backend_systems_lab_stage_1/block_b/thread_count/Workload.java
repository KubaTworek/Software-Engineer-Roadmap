package pl.jakubtworek.backend_systems_lab_stage_1.block_b.thread_count;

public interface Workload {

    // One unit of work executed repeatedly by worker threads.
    //
    // Different implementations simulate different bottlenecks:
    // - CPU-bound,
    // - wait-bound,
    // - mixed.
    long runOneOperation();
}