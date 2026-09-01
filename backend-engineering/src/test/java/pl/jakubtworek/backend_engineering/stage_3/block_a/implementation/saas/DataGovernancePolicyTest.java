package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.saas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class DataGovernancePolicyTest {

    @Test
    void policyMustMakeClassificationRetentionPurposeAndErasureExplicit() {
        DataGovernancePolicy policy = new DataGovernancePolicy(
                "customer.email",
                DataGovernancePolicy.Classification.PII,
                Duration.ofDays(365),
                DataGovernancePolicy.ErasureMode.ANONYMIZE,
                "account-communication");

        assertThat(policy.classification().isPersonal()).isTrue();
        assertThat(policy.retention()).isEqualTo(Duration.ofDays(365));
    }

    @Test
    void retentionAfterErasureMustNameTheLegalObligation() {
        assertThatIllegalArgumentException().isThrownBy(() -> new DataGovernancePolicy(
                "customer.email",
                DataGovernancePolicy.Classification.PII,
                Duration.ofDays(3650),
                DataGovernancePolicy.ErasureMode.RETAIN_FOR_LEGAL_OBLIGATION,
                "analytics"));
    }
}
