package pl.jakubtworek.marketplace.payment.application;

import org.junit.jupiter.api.Test;
import pl.jakubtworek.marketplace.payment.domain.Payment;
import pl.jakubtworek.marketplace.payment.domain.PaymentReserved;
import pl.jakubtworek.marketplace.payment.domain.PaymentStatus;
import pl.jakubtworek.marketplace.payment.infrastructure.InMemoryPaymentRepository;
import pl.jakubtworek.marketplace.shared.kernel.Money;
import pl.jakubtworek.marketplace.testsupport.RecordingEventPublisher;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ReconcileUnknownPaymentUseCaseTest {
    @Test void resolvesByLookupWithoutExecutingReserveAgain() {
        UUID orderId = UUID.randomUUID();
        var repository = new InMemoryPaymentRepository();
        repository.save(Payment.uncertain(orderId, Money.of("20", "PLN"), "provider-7", "timeout"));
        var reserveCalls = new AtomicInteger();
        PaymentGateway gateway = new PaymentGateway() {
            @Override public PaymentReservationResult reserve(UUID ignored, Money amount) {
                reserveCalls.incrementAndGet(); throw new AssertionError("must not reserve twice");
            }
            @Override public PaymentReservationResult lookup(String operationId) {
                assertThat(operationId).isEqualTo("provider-7");
                return new PaymentReservationResult(Outcome.ACCEPTED, operationId, "found");
            }
        };
        var events = new RecordingEventPublisher();

        var status = new ReconcileUnknownPaymentUseCase(gateway, repository, events)
                .reconcile(orderId, UUID.randomUUID());

        assertThat(status).isEqualTo(PaymentStatus.RESERVED);
        assertThat(reserveCalls).hasValue(0);
        assertThat(events.events()).singleElement().isInstanceOf(PaymentReserved.class);
    }
}
