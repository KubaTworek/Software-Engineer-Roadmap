package pl.jakubtworek.backend_engineering.stage_1.block_b.thread_count;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ExperimentConfigTest {

    @Test
    void shouldParseAllSupportedArguments() {
        ExperimentConfig config = ExperimentConfig.fromArgs(new String[]{
                "--mode=mixed",
                "--threads=8",
                "--durationSeconds=5",
                "--cpuIterations=1000",
                "--waitMillis=25"
        });

        assertThat(config).isEqualTo(new ExperimentConfig("mixed", 8, 5, 1000, 25));
    }

    @Test
    void shouldRejectInvalidConfiguration() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ExperimentConfig("unknown", 1, 1, 1, 1));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ExperimentConfig("cpu", 0, 1, 1, 1));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ExperimentConfig("wait", 1, 0, 1, 1));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ExperimentConfig("mixed", 1, 1, -1, 1));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ExperimentConfig("mixed", 1, 1, 1, -1));
    }

    @Test
    void shouldRejectUnknownArgumentInsteadOfSilentlyIgnoringTypo() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ExperimentConfig.fromArgs(new String[]{"--treads=8"}))
                .withMessageContaining("--treads=8");
    }
}
