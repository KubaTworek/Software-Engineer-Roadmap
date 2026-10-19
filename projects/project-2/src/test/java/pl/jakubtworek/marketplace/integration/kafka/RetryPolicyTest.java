package pl.jakubtworek.marketplace.integration.kafka;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class RetryPolicyTest {
    @Test void doesNotRetryPermanentContractFailure() {
        assertThat(RetryPolicy.defaultPolicy().shouldRetry(new IllegalArgumentException("invalid payload"), 1)).isFalse();
    }

    @Test void capsAttemptsAndAddsStableJitter() {
        var policy = RetryPolicy.defaultPolicy();
        UUID eventId = UUID.fromString("00000000-0000-0000-0000-000000000123");
        assertThat(policy.delayFor(2, eventId)).isEqualTo(policy.delayFor(2, eventId));
        assertThat(policy.shouldRetry(new RuntimeException("broker unavailable"), 2)).isTrue();
        assertThat(policy.shouldRetry(new RuntimeException("broker unavailable"), 3)).isFalse();
    }
}
