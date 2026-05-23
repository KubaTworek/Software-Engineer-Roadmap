package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.src.main.java.pl.jakubtworek.systemdesign.implementation;

/**
 * Describes the dominant resource profile of a workload.
 */
public enum WorkloadType {
    CPU_BOUND,
    IO_BOUND,
    MEMORY_BOUND,
    QUEUE_DRIVEN,
    MIXED
}
