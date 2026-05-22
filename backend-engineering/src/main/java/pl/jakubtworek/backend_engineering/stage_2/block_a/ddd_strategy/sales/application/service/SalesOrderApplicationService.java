package pl.jakubtworek.backend_engineering.stage_2.block_a.ddd_strategy.sales.application.service;

// Application service boundary for Sales.
// The implementation belongs to the Sales context.
public interface SalesOrderApplicationService {

    void markOrderAsPaid(
            String orderId,
            String paymentId
    );
}