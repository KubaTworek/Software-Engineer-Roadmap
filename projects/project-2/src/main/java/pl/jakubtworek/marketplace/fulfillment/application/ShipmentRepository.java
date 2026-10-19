package pl.jakubtworek.marketplace.fulfillment.application;

import pl.jakubtworek.marketplace.fulfillment.domain.Shipment;
import java.util.Optional;
import java.util.UUID;

public interface ShipmentRepository {
    Shipment saveIfAbsent(Shipment shipment);
    Optional<Shipment> findByOrderId(UUID orderId);
}
