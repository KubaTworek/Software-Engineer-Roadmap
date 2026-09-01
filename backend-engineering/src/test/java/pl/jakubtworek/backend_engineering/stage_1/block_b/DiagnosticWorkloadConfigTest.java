package pl.jakubtworek.backend_engineering.stage_1.block_b;

import org.junit.jupiter.api.Test;
import pl.jakubtworek.backend_engineering.stage_1.block_b.cpu_vs_io.ProfilingConfig;
import pl.jakubtworek.backend_engineering.stage_1.block_b.g1_vs_zgc.WorkloadConfig;
import pl.jakubtworek.backend_engineering.stage_1.block_b.object_pooling.PoolingConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class DiagnosticWorkloadConfigTest {

    @Test
    void profilingScenarioParsesEverySupportedWorkloadParameter() {
        ProfilingConfig config = ProfilingConfig.fromArgs(new String[]{
                "--durationSeconds=10",
                "--cpuIterations=2000",
                "--simulatedIoMillis=0",
                "--mixedCpuIterations=500",
                "--mixedWaitMillis=2"
        });

        assertThat(config).isEqualTo(new ProfilingConfig(10, 2000, 0, 500, 2));
    }

    @Test
    void gcScenarioRejectsImpossibleWorkloadInsteadOfProducingMisleadingRecording() {
        assertThatIllegalArgumentException().isThrownBy(() -> new WorkloadConfig(
                0, 100, 512, 32, 2, 1
        ));
        assertThatIllegalArgumentException().isThrownBy(() -> new WorkloadConfig(
                1, 100, 0, 32, 2, 1
        ));
        assertThatIllegalArgumentException().isThrownBy(() -> WorkloadConfig.fromArgs(
                new String[]{"--collector=G1"}
        )).withMessageContaining("--collector=G1");
    }

    @Test
    void lockScenarioValidatesThreadAndDurationInputs() {
        pl.jakubtworek.backend_engineering.stage_1.block_b.lock_contention.ExperimentConfig config =
                pl.jakubtworek.backend_engineering.stage_1.block_b.lock_contention.ExperimentConfig.fromArgs(
                        new String[]{"--threads=8", "--durationSecondsPerScenario=3"}
                );

        assertThat(config.threadCount()).isEqualTo(8);
        assertThat(config.durationSecondsPerScenario()).isEqualTo(3);
        assertThatIllegalArgumentException().isThrownBy(() ->
                new pl.jakubtworek.backend_engineering.stage_1.block_b.lock_contention.ExperimentConfig(0, 1)
        );
    }

    @Test
    void poolingScenarioAllowsNoPoolAsBaselineButRejectsInvalidWorkers() {
        PoolingConfig noPoolBaseline = new PoolingConfig(100, 64, 0, 1, 0);

        assertThat(noPoolBaseline.poolSize()).isZero();
        assertThatIllegalArgumentException().isThrownBy(() ->
                new PoolingConfig(100, 64, 10, 0, 0)
        );
        assertThatIllegalArgumentException().isThrownBy(() -> PoolingConfig.fromArgs(
                new String[]{"--workes=4"}
        )).withMessageContaining("--workes=4");
    }
}
