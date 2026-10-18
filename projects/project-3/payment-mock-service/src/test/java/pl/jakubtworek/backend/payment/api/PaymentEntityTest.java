package pl.jakubtworek.backend.payment.api;

import org.junit.jupiter.api.Test;
import pl.jakubtworek.backend.payment.persistence.PaymentEntity;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentEntityTest {
    @Test
    void replayMustPreservePayloadAndRefundIsASeparateIdempotentEffect() {
        PaymentRequest request = new PaymentRequest(UUID.randomUUID(), "user", new BigDecimal("10.00"));
        PaymentEntity payment = PaymentEntity.paid("payment-key", request, Instant.EPOCH);

        payment.requireSameRequest(request);
        assertThatThrownBy(() -> payment.requireSameRequest(
                new PaymentRequest(request.orderId(), "other-user", request.amount())))
                .isInstanceOf(IllegalArgumentException.class);

        payment.refund("refund-key", Instant.EPOCH.plusSeconds(1));
        payment.refund("refund-key", Instant.EPOCH.plusSeconds(2));
        assertThat(payment.toResponse().status()).isEqualTo("REFUNDED");
        assertThatThrownBy(() -> payment.refund("different-refund", Instant.EPOCH.plusSeconds(3)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
