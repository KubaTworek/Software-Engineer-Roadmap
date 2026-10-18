package com.example.paymentsystem.payment;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentInvariantTest {

    @Test
    void refundCanNeverExceedCapturedFunds() {
        Payment payment = pending(CaptureMode.AUTOMATIC, 10_000);
        payment.succeed();
        payment.refund(2_500);

        assertThatThrownBy(() -> payment.refund(7_501))
                .isInstanceOf(PaymentException.class);
        assertThat(payment.getRefundedAmount()).isEqualTo(2_500);
    }

    @Test
    void timeoutLeavesAnExplicitUnknownStateInsteadOfPretendingFailure() {
        Payment payment = base(CaptureMode.AUTOMATIC, 10_000);
        payment.startProviderAttempt();
        payment.markProviderOutcomeUnknown();

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.UNKNOWN);
        assertThat(payment.getCapturedAmount()).isZero();
    }

    @Test
    void staleSuccessCannotMoveRefundedPaymentBackwards() {
        Payment payment = pending(CaptureMode.AUTOMATIC, 10_000);
        payment.succeed();
        payment.refund(10_000);

        assertThatThrownBy(() -> payment.reconcileSucceeded("psp_duplicate"))
                .isInstanceOf(PaymentException.class);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    private Payment pending(CaptureMode mode, long amount) {
        Payment payment = base(mode, amount);
        payment.startProviderAttempt();
        payment.attachProviderPayment("psp_1", "https://psp.local/checkout/1");
        return payment;
    }

    private Payment base(CaptureMode mode, long amount) {
        Payment payment = new Payment(UUID.randomUUID(), "order-1", "customer-1", amount, "PLN",
                mode, PaymentProvider.PAYU_MOCK, 0, RiskDecision.ALLOW);
        payment.applySplit(1_000, amount - 1_000);
        return payment;
    }
}
