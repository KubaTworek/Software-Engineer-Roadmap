package pl.jakubtworek.backend_engineering.stage_1.block_b.cpu_vs_io;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class WorkloadCorrectnessTest {

    @Test
    void shouldCountPrimesBelowExclusiveLimit() {
        assertThat(PrimeCalculator.countPrimes(2)).isZero();
        assertThat(PrimeCalculator.countPrimes(10)).isEqualTo(4);
        assertThat(PrimeCalculator.countPrimes(100)).isEqualTo(25);
    }

    @Test
    void shouldRejectNegativeSimulatedLatency() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> SimulatedIo.callExternalService(-1));
    }
}
