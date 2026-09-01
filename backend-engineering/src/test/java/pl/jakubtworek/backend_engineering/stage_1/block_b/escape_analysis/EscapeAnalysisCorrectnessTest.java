package pl.jakubtworek.backend_engineering.stage_1.block_b.escape_analysis;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EscapeAnalysisCorrectnessTest {

    @Test
    void escapeVariantsReturnTheSameValue() {
        EscapeAnalysisBenchmark benchmark = new EscapeAnalysisBenchmark();
        EscapeState state = new EscapeState();

        assertThat(benchmark.objectDoesNotEscape()).isEqualTo(21);
        assertThat(benchmark.objectEscapesThroughField(state)).isEqualTo(21);
        assertThat(benchmark.objectEscapesThroughReturn()).isEqualTo(21);
        assertThat(state.point.sum()).isEqualTo(21);
    }

    @Test
    void lockElisionVariantsPreserveTheSameCriticalSectionResult() {
        LockElisionBenchmark benchmark = new LockElisionBenchmark();

        assertThat(benchmark.lockDoesNotEscape()).isEqualTo(42);
        assertThat(benchmark.lockEscapesThroughField(new LockState())).isEqualTo(42);
    }
}
