package pl.jakubtworek.backend_engineering.stage_2.block_c.progressive_delivery;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CanaryAndRollbackTest {

    private final CanaryAnalyzer analyzer = new CanaryAnalyzer(
            new CanaryAnalyzer.Policy(1_000, 0.02, 0.01, 1.25));

    @Test
    void holdsUntilBothWindowsHaveEnoughTraffic() {
        CanaryAnalyzer.Analysis analysis = analyzer.analyze(
                new ServiceMetrics(10_000, 50, 100),
                new ServiceMetrics(100, 0, 80));

        assertThat(analysis.decision()).isEqualTo(CanaryAnalyzer.Decision.HOLD);
    }

    @Test
    void automaticallyRollsBackOnErrorRateOrP99Regression() {
        ProgressiveDeliveryController controller = new ProgressiveDeliveryController("orders@sha256:stable");
        controller.startCanary("orders@sha256:candidate", 10);

        CanaryAnalyzer.Analysis analysis = controller.apply(
                analyzer,
                new ServiceMetrics(10_000, 50, 100),
                new ServiceMetrics(2_000, 60, 145));

        assertThat(analysis.decision()).isEqualTo(CanaryAnalyzer.Decision.ROLLBACK);
        assertThat(analysis.reasons()).contains(
                "canary error rate exceeds absolute limit",
                "canary error rate regressed against baseline",
                "canary p99 regressed against baseline");
        assertThat(controller.state().phase()).isEqualTo(ProgressiveDeliveryController.Phase.ROLLED_BACK);
        assertThat(controller.state().candidateTrafficPercent()).isZero();
        assertThat(controller.state().stableRevision()).isEqualTo("orders@sha256:stable");
    }

    @Test
    void promotesHealthyCandidate() {
        ProgressiveDeliveryController controller = new ProgressiveDeliveryController("orders@sha256:stable");
        controller.startCanary("orders@sha256:candidate", 10);

        controller.apply(analyzer,
                new ServiceMetrics(10_000, 100, 100),
                new ServiceMetrics(2_000, 20, 110));

        assertThat(controller.state().phase()).isEqualTo(ProgressiveDeliveryController.Phase.PROMOTED);
        assertThat(controller.state().stableRevision()).isEqualTo("orders@sha256:candidate");
    }
}
