package pl.jakubtworek.backend_engineering.stage_1.block_b.naive_vs_jmh;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BenchmarkTargetCorrectnessTest {

    @Test
    void targetIsDeterministicButDependsOnRuntimeInput() {
        assertThat(BenchmarkTarget.compute(42)).isEqualTo(BenchmarkTarget.compute(42));
        assertThat(BenchmarkTarget.compute(42)).isNotEqualTo(BenchmarkTarget.compute(43));
    }
}
