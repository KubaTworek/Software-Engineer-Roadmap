package pl.jakubtworek.backend_engineering.stage_2.block_b.domain.payments;

import java.math.BigDecimal;

/**
 * Domain object representing a payment attempt.
 *
 * This class belongs to the Payment Service boundary.
 */
public class Payment {

    private final String paymentId;
    private final String orderId;
    private final BigDecimal amount;
    private PaymentStatus status;
    private String failureReason;

    public Payment(
            String paymentId,
            String orderId,
            BigDecimal amount
    ) {
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.amount = amount;
        this.status = PaymentStatus.PENDING;
    }

    /**
     * Marks the payment as authorized.
     */
    public void authorize() {
        if (status != PaymentStatus.PENDING) {
            throw new IllegalStateException("Only pending payments can be authorized.");
        }

        this.status = PaymentStatus.AUTHORIZED;
    }

    /**
     * Marks the payment as failed and stores the failure reason.
     */
    public void fail(String reason) {
        if (status != PaymentStatus.PENDING) {
            throw new IllegalStateException("Only pending payments can fail.");
        }

        this.status = PaymentStatus.FAILED;
        this.failureReason = reason;
    }

    public String paymentId() {
        return paymentId;
    }

    public String orderId() {
        return orderId;
    }

    public BigDecimal amount() {
        return amount;
    }

    public PaymentStatus status() {
        return status;
    }

    public String failureReason() {
        return failureReason;
    }
}