package pl.jakubtworek.backend_engineering.stage_2.block_a.integration.sales.saga;

import java.util.Optional;

// Repository for saga state.
// The orchestrator uses it to recover and continue long-running processes.
public interface OrderSagaRepository {

    Optional<OrderSaga> findByOrderId(String orderId);

    void save(OrderSaga saga);
}