package pl.jakubtworek.backend_systems_lab_stage_1.block_b.false_sharing;

public final class PaddedLong {

    // Padding before the value.
    // These fields try to occupy space around the hot value.
    //
    // This is a manual demonstration technique.
    // In real production code, prefer higher-level designs or @Contended when appropriate.
    public long p1, p2, p3, p4, p5, p6, p7;

    // This is the hot field updated by a thread.
    public volatile long value = 0L;

    // Padding after the value.
    // This reduces the chance that another hot field lands in the same cache line.
    public long p8, p9, p10, p11, p12, p13, p14;
}