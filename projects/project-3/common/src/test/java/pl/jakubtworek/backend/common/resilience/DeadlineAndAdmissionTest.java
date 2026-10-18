package pl.jakubtworek.backend.common.resilience;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeadlineAndAdmissionTest {
    @Test
    void retryReceivesOnlyRemainingEndToEndBudget() {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        var budget = new DeadlineBudget(start.plusSeconds(2));
        assertThat(budget.attemptTimeout(start.plusMillis(1500), Duration.ofSeconds(2), Duration.ofMillis(100)))
                .isEqualTo(Duration.ofMillis(400));
    }

    @Test
    void shedsLoadInsteadOfBuildingUnboundedQueue() {
        var controller = new AdmissionController(1);
        try (var ignored = controller.tryAcquire()) {
            assertThatThrownBy(controller::tryAcquire).isInstanceOf(AdmissionController.LoadShedException.class);
        }
        try (var acceptedAfterRelease = controller.tryAcquire()) {
            assertThat(acceptedAfterRelease).isNotNull();
        }
    }
}
