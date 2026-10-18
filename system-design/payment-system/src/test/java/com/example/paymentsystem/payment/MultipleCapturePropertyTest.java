package com.example.paymentsystem.payment;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.IntRange;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MultipleCapturePropertyTest {
    @Property
    void anyTwoValidPartialCapturesPreserveTheAuthorizationCeiling(
            @ForAll @IntRange(min = 1, max = 9_999) int first) {
        Payment payment = new Payment(UUID.randomUUID(), "order", "customer", 10_000, "PLN",
                CaptureMode.MANUAL, PaymentMethod.CARD, PaymentProvider.PAYU_MOCK, 1, RiskDecision.ALLOW);
        payment.startProviderAttempt();
        payment.attachProviderPayment("provider", "checkout");
        payment.authorize();

        payment.capture(first);
        payment.capture(10_000 - first);

        assertThat(payment.getCapturedAmount()).isEqualTo(10_000);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CAPTURED);
    }
}
