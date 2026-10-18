package pl.jakubtworek.backend.order.domain;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import java.time.Duration;

class OrderEntityTest {
    @Test
    void distinguishesUnknownPaymentFromFailedReservationConfirmation() {
        var order = OrderEntity.pending(UUID.randomUUID(), "user", "key", "fingerprint", Instant.EPOCH);
        order.markPaymentUnknown("timeout");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_UNKNOWN);

        order.markReservationConfirmationPending("reservation unavailable");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.RESERVATION_CONFIRMATION_PENDING);
    }

    @Test
    void reconciliationBackoffGrowsButRemainsBounded() {
        var order = OrderEntity.pending(UUID.randomUUID(), "user", "key", "fingerprint", Instant.EPOCH);
        order.markPaymentUnknown("timeout");
        for (int attempt = 0; attempt < 20; attempt++) order.scheduleReconciliation("timeout", Instant.EPOCH);
        assertThat(Duration.between(Instant.EPOCH, order.getNextAttemptAt())).isEqualTo(Duration.ofMinutes(5));
        assertThat(order.getReconciliationAttempts()).isEqualTo(20);
    }

    @Test
    void compensationAndManualReviewAreExplicitTerminalDecisions() {
        var compensated = OrderEntity.pending(UUID.randomUUID(), "user", "key", "fingerprint", Instant.EPOCH);
        compensated.markReservationConfirmationPending("downstream unavailable");
        compensated.markCompensationPending("retry budget exhausted", Instant.EPOCH);
        assertThat(compensated.getStatus()).isEqualTo(OrderStatus.COMPENSATION_PENDING);
        compensated.markFailed("refunded");
        assertThat(compensated.getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(compensated.getNextAttemptAt()).isNull();

        var ambiguous = OrderEntity.pending(UUID.randomUUID(), "user", "other", "fingerprint", Instant.EPOCH);
        ambiguous.markPaymentUnknown("timeout after sending request");
        ambiguous.markManualReview("lookup budget exhausted");
        assertThat(ambiguous.getStatus()).isEqualTo(OrderStatus.MANUAL_REVIEW);
        assertThat(ambiguous.getNextAttemptAt()).isNull();
    }

    @Test
    void eachSagaStepGetsAnIndependentRetryBudget() {
        var order = OrderEntity.pending(UUID.randomUUID(), "user", "key", "fingerprint", Instant.EPOCH);
        order.markPaymentUnknown("timeout");
        for (int attempt = 0; attempt < 4; attempt++) order.scheduleReconciliation("lookup", Instant.EPOCH);

        order.markReservationConfirmationPending("payment lookup returned PAID");

        assertThat(order.getReconciliationAttempts()).isZero();
        assertThat(order.getNextAttemptAt()).isNull();
    }
}
