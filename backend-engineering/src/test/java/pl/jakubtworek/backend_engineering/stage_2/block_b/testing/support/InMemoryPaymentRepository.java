package pl.jakubtworek.backend_engineering.stage_2.block_b.testing.support;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * In-memory payment repository used in tests.
 *
 * It uses orderId as a business key so that repeated processing of the same
 * OrderPlaced event cannot create duplicated payment records.
 */
public class InMemoryPaymentRepository {

    private final Map<String, PaymentRecord> paymentsByOrderId = new HashMap<>();

    /**
     * Creates a payment record if it does not already exist.
     *
     * This method models an UPSERT-like operation.
     */
    public void createIfAbsent(String orderId, BigDecimal amount) {
        paymentsByOrderId.putIfAbsent(
                orderId,
                new PaymentRecord(orderId, amount, "AUTHORIZED")
        );
    }

    /**
     * Returns the number of payment records.
     */
    public int count() {
        return paymentsByOrderId.size();
    }

    /**
     * Returns true when a payment exists for the given order.
     */
    public boolean existsForOrder(String orderId) {
        return paymentsByOrderId.containsKey(orderId);
    }

    /**
     * Clears repository state between tests.
     */
    public void clear() {
        paymentsByOrderId.clear();
    }
}