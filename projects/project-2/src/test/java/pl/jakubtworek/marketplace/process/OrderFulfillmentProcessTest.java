package pl.jakubtworek.marketplace.process;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class OrderFulfillmentProcessTest {
    @Test
    void requestsPaymentCompensationWhenStockFailsAfterPaymentWasReserved() {
        var process = new OrderFulfillmentProcess(UUID.randomUUID());
        process.paymentReserved();

        assertThat(process.stockRejected())
                .containsExactly(OrderFulfillmentProcess.Step.PAYMENT_RELEASE_REQUESTED);
        assertThat(process.status()).isEqualTo(OrderFulfillmentProcess.Status.COMPENSATING);

        process.compensationCompleted();
        process.compensationCompleted(); // redelivery nie może zmienić stanu terminalnego
        assertThat(process.status()).isEqualTo(OrderFulfillmentProcess.Status.CANCELLED);
    }

    @Test
    void duplicateSuccessEventsDoNotProduceExtraTransitions() {
        var process = new OrderFulfillmentProcess(UUID.randomUUID());
        process.paymentReserved();
        process.paymentReserved();
        process.stockReserved();
        assertThat(process.status()).isEqualTo(OrderFulfillmentProcess.Status.CONFIRMED);
        assertThat(process.completedSteps()).containsExactlyInAnyOrder(
                OrderFulfillmentProcess.Step.PAYMENT_RESERVED,
                OrderFulfillmentProcess.Step.STOCK_RESERVED);
    }

    @Test
    void expiresWaitingProcessAndRequestsOnlyNecessaryCompensation() {
        Instant deadline = Instant.parse("2026-01-01T10:00:00Z");
        var process = new OrderFulfillmentProcess(UUID.randomUUID(), deadline);
        process.paymentReserved();

        assertThat(process.expire(deadline.minusMillis(1))).isEmpty();
        assertThat(process.expire(deadline))
                .containsExactly(OrderFulfillmentProcess.Step.PAYMENT_RELEASE_REQUESTED);
        assertThat(process.status()).isEqualTo(OrderFulfillmentProcess.Status.COMPENSATING);
    }

    @Test
    void restoredSnapshotKeepsFactsAcrossApplicationRestart() {
        UUID orderId = UUID.randomUUID();
        var beforeRestart = new OrderFulfillmentProcess(orderId, Instant.parse("2026-01-01T10:15:00Z"));
        beforeRestart.paymentReserved();
        beforeRestart.persistedAs(7L);

        var afterRestart = OrderFulfillmentProcess.restore(orderId, beforeRestart.deadline(),
                beforeRestart.status(), beforeRestart.completedSteps(), beforeRestart.version());
        afterRestart.stockReserved();

        assertThat(afterRestart.status()).isEqualTo(OrderFulfillmentProcess.Status.CONFIRMED);
        assertThat(afterRestart.version()).isEqualTo(7L);
    }

    @Test
    void lateSuccessAfterRejectionIsCompensatedInsteadOfIgnored() {
        var process = new OrderFulfillmentProcess(UUID.randomUUID());

        process.stockRejected();
        assertThat(process.status()).isEqualTo(OrderFulfillmentProcess.Status.PENDING);

        // Różne topiki nie zapewniają globalnej kolejności: sukces mógł już powstać.
        process.paymentReserved();

        assertThat(process.status()).isEqualTo(OrderFulfillmentProcess.Status.COMPENSATING);
        assertThat(process.completedSteps())
                .contains(OrderFulfillmentProcess.Step.STOCK_REJECTED,
                        OrderFulfillmentProcess.Step.PAYMENT_RESERVED,
                        OrderFulfillmentProcess.Step.PAYMENT_RELEASE_REQUESTED);
    }

    @Test
    void finishesWithoutCompensationOnlyAfterBothResourcesReject() {
        var process = new OrderFulfillmentProcess(UUID.randomUUID());
        process.paymentRejected();
        assertThat(process.status()).isEqualTo(OrderFulfillmentProcess.Status.PENDING);

        process.stockRejected();

        assertThat(process.status()).isEqualTo(OrderFulfillmentProcess.Status.CANCELLED);
    }
}
