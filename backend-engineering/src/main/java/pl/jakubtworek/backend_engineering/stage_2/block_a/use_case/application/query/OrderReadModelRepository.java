package pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.application.query;

import java.util.Optional;

// Repository for query model.
// It serves read use cases and should not be used to modify aggregates.
public interface OrderReadModelRepository {

    void save(OrderReadModel readModel);

    Optional<OrderReadModel> findByOrderId(String orderId);
}