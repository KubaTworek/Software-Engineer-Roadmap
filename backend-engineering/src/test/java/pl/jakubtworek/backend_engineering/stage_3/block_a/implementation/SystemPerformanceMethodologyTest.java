package pl.jakubtworek.backend_engineering.stage_3.block_a.implementation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.capacity.BottleneckType;
import pl.jakubtworek.backend_engineering.stage_3.block_a.concepts.capacity.CapacityPlan;
import pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.tests.CapacityHypothesis;
import pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.tests.LatencyMeasurement;
import pl.jakubtworek.backend_engineering.stage_3.block_a.implementation.tests.WorkloadProfile;

class SystemPerformanceMethodologyTest {

    @Test
    void capacityHypothesisMustNameFirstBottleneckAndReserveHeadroomBeforeTest() {
        CapacityPlan plan = new CapacityPlan(
                2, 2, 0.7, 0.01,
                50, 1.0, 0.1,
                1_000, 0.5, 2);

        CapacityHypothesis hypothesis = CapacityHypothesis.from(plan, 0.2);

        assertThat(hypothesis.firstBottleneck().type()).isEqualTo(BottleneckType.API_CPU);
        assertThat(hypothesis.firstBottleneck().limitRps()).isEqualTo(280.0);
        assertThat(hypothesis.safeRps()).isEqualTo(224.0);
    }

    @Test
    void openAndClosedModelsMustNotBeConfiguredAsIfTheyWereEquivalent() {
        assertThat(WorkloadProfile.openAtRps(500))
                .isEqualTo(new WorkloadProfile(WorkloadProfile.Model.OPEN, 500, 0));
        assertThat(WorkloadProfile.closedWithUsers(50))
                .isEqualTo(new WorkloadProfile(WorkloadProfile.Model.CLOSED, 0, 50));

        assertThatThrownBy(() -> new WorkloadProfile(WorkloadProfile.Model.OPEN, 500, 50))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void scheduleCorrectedLatencyMustExposeCoordinatedOmission() {
        Instant scheduled = Instant.parse("2026-01-01T00:00:00Z");
        Instant startedLate = scheduled.plusMillis(900);
        Instant completed = scheduled.plusSeconds(1);

        LatencyMeasurement latency = LatencyMeasurement.from(scheduled, startedLate, completed);

        assertThat(latency.observed()).isEqualTo(Duration.ofMillis(100));
        assertThat(latency.scheduleCorrected()).isEqualTo(Duration.ofSeconds(1));
    }
}
