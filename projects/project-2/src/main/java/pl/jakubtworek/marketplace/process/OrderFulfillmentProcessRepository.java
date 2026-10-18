package pl.jakubtworek.marketplace.process;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Port trwałości process managera; wersja chroni przed utratą równoległego faktu. */
public interface OrderFulfillmentProcessRepository {
    Optional<OrderFulfillmentProcess> findByOrderId(UUID orderId);
    OrderFulfillmentProcess save(OrderFulfillmentProcess process);
    List<OrderFulfillmentProcess> findExpiredPending(int limit);
}
