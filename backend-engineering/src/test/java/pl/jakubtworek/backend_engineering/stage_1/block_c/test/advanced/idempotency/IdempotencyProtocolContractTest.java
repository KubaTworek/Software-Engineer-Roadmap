package pl.jakubtworek.backend_engineering.stage_1.block_c.test.advanced.idempotency;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static pl.jakubtworek.backend_engineering.stage_1.block_c.test.advanced.idempotency.IdempotencyProtocol.BeginStatus.CONFLICT;
import static pl.jakubtworek.backend_engineering.stage_1.block_c.test.advanced.idempotency.IdempotencyProtocol.BeginStatus.IN_PROGRESS;
import static pl.jakubtworek.backend_engineering.stage_1.block_c.test.advanced.idempotency.IdempotencyProtocol.BeginStatus.REPLAY;
import static pl.jakubtworek.backend_engineering.stage_1.block_c.test.advanced.idempotency.IdempotencyProtocol.BeginStatus.STARTED;

class IdempotencyProtocolContractTest {

    @Test
    void shouldExposeEveryProtocolOutcomeWithoutRepeatingTheEffect() {
        IdempotencyProtocol protocol = new IdempotencyProtocol();

        assertThat(protocol.begin("payment-7", "amount=100").status()).isEqualTo(STARTED);
        assertThat(protocol.begin("payment-7", "amount=100").status()).isEqualTo(IN_PROGRESS);
        assertThat(protocol.begin("payment-7", "amount=200").status()).isEqualTo(CONFLICT);

        assertThat(protocol.complete("payment-7", "amount=100", "receipt-9"))
                .isEqualTo(IdempotencyProtocol.CompletionStatus.COMPLETED);

        IdempotencyProtocol.BeginDecision replay = protocol.begin("payment-7", "amount=100");
        assertThat(replay.status()).isEqualTo(REPLAY);
        assertThat(replay.replayedResult()).contains("receipt-9");
        assertThat(protocol.complete("payment-7", "amount=100", "receipt-9"))
                .isEqualTo(IdempotencyProtocol.CompletionStatus.ALREADY_COMPLETED);
    }

    @Test
    void shouldRejectCompletionThatCouldCorruptTheRecordedOperation() {
        IdempotencyProtocol protocol = new IdempotencyProtocol();

        assertThatThrownBy(() -> protocol.complete("unknown", "request-a", "result"))
                .isInstanceOf(IllegalStateException.class);

        protocol.begin("key", "request-a");
        assertThatThrownBy(() -> protocol.complete("key", "request-b", "result"))
                .isInstanceOf(IdempotencyProtocol.IdempotencyConflictException.class);

        protocol.complete("key", "request-a", "result-a");
        assertThatThrownBy(() -> protocol.complete("key", "request-a", "result-b"))
                .isInstanceOf(IdempotencyProtocol.IdempotencyConflictException.class);
    }

    @Test
    void shouldValidateInputAndExposeAnImmutableSnapshot() {
        IdempotencyProtocol protocol = new IdempotencyProtocol();

        assertThatThrownBy(() -> protocol.begin(" ", "request"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> protocol.begin("key", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> protocol.complete(" ", "request", "result"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> protocol.complete("key", null, "result"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> protocol.complete("key", "request", null))
                .isInstanceOf(NullPointerException.class);
        assertThat(protocol.snapshot("missing")).isEmpty();

        protocol.begin("key", "request");
        assertThat(protocol.snapshot("key")).contains(new IdempotencyProtocol.ProtocolSnapshot(
                "request",
                IdempotencyProtocol.ProtocolState.PROCESSING,
                Optional.empty()
        ));
    }
}
