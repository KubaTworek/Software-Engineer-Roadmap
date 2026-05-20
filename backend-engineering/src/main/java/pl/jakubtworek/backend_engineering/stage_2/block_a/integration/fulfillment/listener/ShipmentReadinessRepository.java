package pl.jakubtworek.backend_engineering.stage_2.block_a.integration.fulfillment.listener;

import java.util.Optional;

// Repository storing local process state needed by Fulfillment.
// This avoids querying Billing or Inventory databases directly.
public interface ShipmentReadinessRepository {

    Optional<ShipmentReadiness> findByOrderId(String orderId);

    void save(ShipmentReadiness readiness);
}