package com.example.ecommerce.payment;

import com.example.ecommerce.order.CustomerOrder;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class PaymentStateMachineTest {

    @Test
    void successfulPaymentCannotLaterBecomeFailed() {
        Payment payment = new Payment(
                mock(CustomerOrder.class), "PSP", BigDecimal.TEN, "PLN", "checkout-1");

        payment.markSucceeded("provider-payment-1");

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThatThrownBy(() -> payment.markFailed("provider-payment-2"))
                .isInstanceOf(IllegalStateException.class);
    }
}
