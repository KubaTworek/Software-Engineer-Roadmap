package pl.jakubtworek.backend_engineering.stage_1.block_f.networking;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class TimeoutPolicyTest {

    @Test
    void requestDeadlineCapsTheReadTimeout() {
        TimeoutPolicy policy = new TimeoutPolicy(Duration.ofMillis(100), Duration.ofSeconds(2), Duration.ofSeconds(1));

        assertThat(policy.effectiveReadTimeout(Duration.ofMillis(850))).isEqualTo(Duration.ofMillis(150));
        assertThat(policy.effectiveReadTimeout(Duration.ofSeconds(1))).isZero();
    }

    @Test
    void detectsProxyThatTimesOutBeforeItsDownstream() {
        TimeoutChainValidator validator = new TimeoutChainValidator();
        List<TimeoutChainValidator.Hop> chain = List.of(
                new TimeoutChainValidator.Hop("client", Duration.ofSeconds(3), Duration.ofMillis(100)),
                new TimeoutChainValidator.Hop("load-balancer", Duration.ofSeconds(4), Duration.ofMillis(100)),
                new TimeoutChainValidator.Hop("service", Duration.ofSeconds(2), Duration.ofMillis(100)));

        assertThat(validator.validateOuterToInner(chain))
                .containsExactly("client must exceed load-balancer timeout plus cleanup margin");
    }
}
