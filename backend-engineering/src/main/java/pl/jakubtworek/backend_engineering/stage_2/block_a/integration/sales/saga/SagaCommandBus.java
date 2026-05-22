package pl.jakubtworek.backend_engineering.stage_2.block_a.integration.sales.saga;

// Command sender used by the saga orchestrator.
// In production this may publish commands to Kafka topics or call internal APIs.
public interface SagaCommandBus {

    void sendAuthorizePayment(String orderId);

    void sendReserveInventory(String orderId);

    void sendScheduleShipment(String orderId);

    void sendCancelPayment(String orderId);

    void sendReleaseInventory(String orderId);
}