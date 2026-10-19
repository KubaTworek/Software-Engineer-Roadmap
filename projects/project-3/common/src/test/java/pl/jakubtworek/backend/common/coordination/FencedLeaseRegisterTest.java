package pl.jakubtworek.backend.common.coordination;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FencedLeaseRegisterTest {
    @Test
    void rejectsWriteFromPreviousOwnerAfterItsLeaseExpired() {
        var register = new FencedLeaseRegister<String>();
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        var workerA = register.acquire("worker-a", start, Duration.ofSeconds(10));
        var workerB = register.acquire("worker-b", start.plusSeconds(11), Duration.ofSeconds(10));

        register.write(workerB, "new-owner-result");

        assertThatThrownBy(() -> register.write(workerA, "stale-result"))
                .isInstanceOf(FencedLeaseRegister.StaleOwnerException.class);
        assertThat(register.value()).isEqualTo("new-owner-result");
    }
}
