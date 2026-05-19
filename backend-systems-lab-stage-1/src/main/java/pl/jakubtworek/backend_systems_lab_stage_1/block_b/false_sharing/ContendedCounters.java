package pl.jakubtworek.backend_systems_lab_stage_1.block_b.false_sharing;

import jdk.internal.vm.annotation.Contended;

public final class ContendedCounters {

    // @Contended tells HotSpot to add padding around the annotated field.
    //
    // This requires:
    // -XX:-RestrictContended
    //
    // It may also require module export flags depending on the build setup.
    @Contended
    public volatile long counter1 = 0L;

    @Contended
    public volatile long counter2 = 0L;
}