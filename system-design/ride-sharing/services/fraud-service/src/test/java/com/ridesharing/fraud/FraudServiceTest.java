package com.ridesharing.fraud;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FraudServiceTest {
    @Test
    void combinedSignalsFailClosedWithExplainableReasons() {
        var result = new FraudService().assess(new RiskAssessmentRequest(
                "driver-1", "DRIVER", "warsaw", 6, 3, 4, 20, true
        ));

        assertThat(result.decision()).isEqualTo(RiskDecision.TEMPORARILY_SUSPEND);
        assertThat(result.score()).isEqualTo(100);
        assertThat(result.reasons()).contains("gps_spoofing_suspected", "payment_failures");
    }
}
