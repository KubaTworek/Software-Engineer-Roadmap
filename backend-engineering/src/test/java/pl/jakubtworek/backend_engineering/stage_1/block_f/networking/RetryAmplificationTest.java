package pl.jakubtworek.backend_engineering.stage_1.block_f.networking;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class RetryAmplificationTest {

    @Test
    void multipliesAttemptsAcrossClientProxyAndMesh() {
        assertThat(RetryAmplification.downstreamAttempts(List.of(3, 3, 3))).isEqualTo(27);
        assertThat(RetryAmplification.fitsBudget(List.of(3, 3, 3), 5)).isFalse();
        assertThat(RetryAmplification.fitsBudget(List.of(1, 1, 3), 5)).isTrue();
    }
}
