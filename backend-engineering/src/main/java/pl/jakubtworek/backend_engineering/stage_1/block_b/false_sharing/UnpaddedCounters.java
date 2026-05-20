package pl.jakubtworek.backend_engineering.stage_1.block_b.false_sharing;

public final class UnpaddedCounters {

    // These fields are independent from the Java program's perspective.
    // However, the CPU may place them on the same cache line.
    //
    // If two different cores update these fields concurrently,
    // they can still fight over the same cache line.
    public volatile long counter1 = 0L;
    public volatile long counter2 = 0L;
}