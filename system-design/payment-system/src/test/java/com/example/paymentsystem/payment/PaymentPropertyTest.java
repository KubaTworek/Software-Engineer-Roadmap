package com.example.paymentsystem.payment;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.LongRange;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

/** Property-based proof over many amounts instead of a few hand-picked fixtures. */
class PaymentPropertyTest {
    @Property(tries = 250)
    void cumulativeRefundCanNeverExceedCapturedMoney(
            @ForAll @LongRange(min = 1, max = 1_000_000) long captured,
            @ForAll @LongRange(min = 0, max = 1_000_000) long candidate) {
        Payment payment = succeededPayment(captured);
        long validRefund = Math.min(candidate, captured);
        if (validRefund > 0) payment.refund(validRefund);

        assertThat(payment.getRefundedAmount()).isEqualTo(validRefund);
        assertThat(payment.getRefundedAmount()).isLessThanOrEqualTo(payment.getCapturedAmount());
        assertThatThrownBy(() -> payment.refund(captured - validRefund + 1))
                .isInstanceOf(PaymentException.class);
    }

    @Property
    void nonCardRedirectMethodsRejectManualCapture(
            @ForAll @LongRange(min = 1, max = 1_000_000) long amount) {
        Payment payment = new Payment(UUID.randomUUID(), "order", "customer", amount, "PLN",
                CaptureMode.MANUAL, PaymentMethod.BANK_REDIRECT, PaymentProvider.PAYU_MOCK, 0,
                RiskDecision.ALLOW);
        payment.startProviderAttempt();
        payment.attachProviderPayment("provider-id", "https://checkout.local");

        assertThatThrownBy(payment::authorize).isInstanceOf(PaymentException.class);
    }

    private Payment succeededPayment(long amount) {
        Payment payment = new Payment(UUID.randomUUID(), "order", "customer", amount, "PLN",
                CaptureMode.AUTOMATIC, PaymentProvider.PAYU_MOCK, 0, RiskDecision.ALLOW);
        payment.startProviderAttempt();
        payment.attachProviderPayment("provider-id", "https://checkout.local");
        payment.succeed();
        return payment;
    }
}
