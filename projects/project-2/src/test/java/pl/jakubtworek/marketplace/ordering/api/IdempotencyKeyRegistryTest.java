package pl.jakubtworek.marketplace.ordering.api;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdempotencyKeyRegistryTest {
    @Test void repeatsResponseWithoutRepeatingCommandAndRejectsKeyReuseForDifferentPayload() {
        var registry = new IdempotencyKeyRegistry();
        var calls = new AtomicInteger();
        UUID expected = UUID.randomUUID();

        var first = registry.execute("checkout-1", "hash-A", () -> { calls.incrementAndGet(); return expected; });
        var replay = registry.execute("checkout-1", "hash-A", () -> { calls.incrementAndGet(); return UUID.randomUUID(); });

        assertThat(first.replayed()).isFalse();
        assertThat(replay.replayed()).isTrue();
        assertThat(replay.value()).isEqualTo(expected);
        assertThat(calls).hasValue(1);
        assertThatThrownBy(() -> registry.execute("checkout-1", "hash-B", UUID::randomUUID))
                .isInstanceOf(IdempotencyKeyRegistry.IdempotencyKeyConflictException.class);
    }
}
