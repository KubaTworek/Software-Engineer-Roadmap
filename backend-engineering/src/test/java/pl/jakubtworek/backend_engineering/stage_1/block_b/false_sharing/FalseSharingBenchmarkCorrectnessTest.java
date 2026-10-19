package pl.jakubtworek.backend_engineering.stage_1.block_b.false_sharing;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FalseSharingBenchmarkCorrectnessTest {

    @Test
    void paddedAndUnpaddedGroupsPerformTheSameNumberOfWrites() {
        FalseSharingBenchmark benchmark = new FalseSharingBenchmark();

        benchmark.unpaddedCounter1();
        benchmark.unpaddedCounter2();
        benchmark.paddedCounter1();
        benchmark.paddedCounter2();

        assertThat(benchmark.unpaddedCounter1Value()).isEqualTo(10_000);
        assertThat(benchmark.unpaddedCounter2Value()).isEqualTo(10_000);
        assertThat(benchmark.paddedCounter1Value()).isEqualTo(10_000);
        assertThat(benchmark.paddedCounter2Value()).isEqualTo(10_000);
    }
}
