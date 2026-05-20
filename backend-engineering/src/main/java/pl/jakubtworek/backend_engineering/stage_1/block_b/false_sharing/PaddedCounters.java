package pl.jakubtworek.backend_engineering.stage_1.block_b.false_sharing;

public final class PaddedCounters {

    // Each counter is wrapped in a padded object.
    // The goal is to reduce the chance that two hot values share one cache line.
    public final PaddedLong counter1 = new PaddedLong();
    public final PaddedLong counter2 = new PaddedLong();
}