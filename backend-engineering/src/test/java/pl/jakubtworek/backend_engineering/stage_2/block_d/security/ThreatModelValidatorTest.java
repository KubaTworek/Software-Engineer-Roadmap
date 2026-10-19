package pl.jakubtworek.backend_engineering.stage_2.block_d.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ThreatModelValidatorTest {

    @Test
    void privilegedSensitiveFlowMustFailClosedWhenControlsAreMissing() {
        SecurityDataFlow flow = new SecurityDataFlow(
                "support exports customer data",
                SecurityDataFlow.TrustZone.INTERNET,
                SecurityDataFlow.TrustZone.DATA,
                SecurityDataFlow.AccessLevel.PRIVILEGED,
                SecurityDataFlow.DataSensitivity.PII,
                Set.of(SecurityDataFlow.SecurityControl.TLS));

        ThreatModelValidator.Review review = new ThreatModelValidator().review(List.of(flow));

        assertThat(review.approved()).isFalse();
        assertThat(review.violations()).extracting(ThreatModelValidator.Violation::missingControl)
                .containsExactlyInAnyOrder(
                        SecurityDataFlow.SecurityControl.INPUT_VALIDATION,
                        SecurityDataFlow.SecurityControl.RATE_LIMIT,
                        SecurityDataFlow.SecurityControl.AUTHENTICATION,
                        SecurityDataFlow.SecurityControl.AUTHORIZATION,
                        SecurityDataFlow.SecurityControl.AUDIT,
                        SecurityDataFlow.SecurityControl.ENCRYPTION_AT_REST);
    }

    @Test
    void referenceThirdPartyFlowMustNameEgressAndAuthenticationControls() {
        SecurityDataFlow flow = new SecurityDataFlow(
                "application calls payment provider",
                SecurityDataFlow.TrustZone.APPLICATION,
                SecurityDataFlow.TrustZone.THIRD_PARTY,
                SecurityDataFlow.AccessLevel.AUTHENTICATED,
                SecurityDataFlow.DataSensitivity.PII,
                Set.of(
                        SecurityDataFlow.SecurityControl.TLS,
                        SecurityDataFlow.SecurityControl.AUTHENTICATION,
                        SecurityDataFlow.SecurityControl.EGRESS_ALLOWLIST));

        assertThat(new ThreatModelValidator().review(List.of(flow)).approved()).isTrue();
    }
}
