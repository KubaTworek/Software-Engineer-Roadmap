package pl.jakubtworek.marketplace.payment.domain;

import org.junit.jupiter.api.Test;
import pl.jakubtworek.marketplace.shared.kernel.Money;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentTest {

    @Test
    void shouldReserveAcceptedPaymentAndRegisterPaymentReservedEvent() {
        UUID orderId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        UUID causationId = UUID.randomUUID();

        Payment payment = Payment.reserve(orderId, Money.of("99.99", "PLN"), true, correlationId, causationId);

        assertThat(payment.status()).isEqualTo(PaymentStatus.RESERVED);
        assertThat(payment.orderId()).isEqualTo(orderId);
        assertThat(payment.domainEvents()).hasSize(1);
        assertThat(payment.domainEvents().getFirst()).isInstanceOf(PaymentReserved.class);

        PaymentReserved event = (PaymentReserved) payment.domainEvents().getFirst();
        assertThat(event.orderId()).isEqualTo(orderId);
        assertThat(event.correlationId()).isEqualTo(correlationId);
        assertThat(event.causationId()).isEqualTo(causationId);
    }

    @Test
    void shouldRejectDeclinedPaymentAndRegisterPaymentRejectedEvent() {
        UUID orderId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        UUID causationId = UUID.randomUUID();

        Payment payment = Payment.reserve(orderId, Money.of("99.99", "PLN"), false, correlationId, causationId);

        assertThat(payment.status()).isEqualTo(PaymentStatus.REJECTED);
        assertThat(payment.domainEvents()).hasSize(1);
        assertThat(payment.domainEvents().getFirst()).isInstanceOf(PaymentRejected.class);
    }

    @Test
    void timeoutAfterSendingRequestStaysUnknownUntilLookupResolvesSameProviderOperation() {
        UUID orderId = UUID.randomUUID();
        Payment payment = Payment.uncertain(orderId, Money.of("99.99", "PLN"), "provider-op-17", "read timeout");

        assertThat(payment.status()).isEqualTo(PaymentStatus.UNKNOWN);
        assertThat(payment.providerOperationId()).isEqualTo("provider-op-17");
        assertThat(payment.domainEvents()).isEmpty();

        payment.resolve(true, UUID.randomUUID(), UUID.randomUUID(), "provider confirms authorization");
        assertThat(payment.status()).isEqualTo(PaymentStatus.RESERVED);
        assertThat(payment.domainEvents()).singleElement().isInstanceOf(PaymentReserved.class);
    }
}
