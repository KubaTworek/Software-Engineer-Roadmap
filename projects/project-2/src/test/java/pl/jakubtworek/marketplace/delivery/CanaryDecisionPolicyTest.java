package pl.jakubtworek.marketplace.delivery;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CanaryDecisionPolicyTest {
    private final CanaryDecisionPolicy policy = new CanaryDecisionPolicy(1_000, .02, 500, .25);

    @Test void rollsBackOnRelativeP99RegressionEvenBelowAbsoluteLimit() {
        assertThat(policy.evaluate(new CanaryDecisionPolicy.Signals(20_000, .005, 200),
                new CanaryDecisionPolicy.Signals(2_000, .006, 300)))
                .isEqualTo(CanaryDecisionPolicy.Decision.ROLLBACK);
    }

    @Test void refusesPromotionWithoutRepresentativeSample() {
        assertThat(policy.evaluate(new CanaryDecisionPolicy.Signals(20_000, .005, 200),
                new CanaryDecisionPolicy.Signals(20, 0, 50)))
                .isEqualTo(CanaryDecisionPolicy.Decision.INCONCLUSIVE);
    }
}
