package pl.jakubtworek.backend_engineering.stage_2.block_b.consumer.payment;

import java.math.BigDecimal;

/**
 * Payment application service.
 *
 * This service should use database constraints or upsert logic to avoid creating
 * duplicate payment records for the same order.
 */
public interface PaymentService {

    /**
     * Authorizes payment for a given order.
     *
     * The implementation should be idempotent by orderId.
     */
    void authorizePayment(String orderId, BigDecimal amount);
}