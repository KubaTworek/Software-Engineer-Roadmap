package pl.jakubtworek.backend_engineering.stage_2.block_a.integration.antipattern;

// Anti-pattern example.
// This interface represents direct access to another context's table.
public interface SharedOrderTableAccess {

    // This method breaks bounded context isolation because another service reads Sales tables directly.
    SharedOrderRow findOrderRow(String orderId);

    // This method also creates hidden coupling to the Sales database schema.
    void updateOrderPaymentStatus(String orderId, String paymentStatus);
}