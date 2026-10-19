package pl.jakubtworek.marketplace.fulfillment.infrastructure;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import pl.jakubtworek.marketplace.fulfillment.application.ShipmentRepository;
import pl.jakubtworek.marketplace.fulfillment.domain.Shipment;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
@Profile("!postgres")
public class InMemoryShipmentRepository implements ShipmentRepository {
    private final ConcurrentHashMap<UUID, Shipment> byOrder = new ConcurrentHashMap<>();
    @Override public Shipment saveIfAbsent(Shipment shipment) {
        return byOrder.computeIfAbsent(shipment.orderId(), ignored -> shipment);
    }
    @Override public Optional<Shipment> findByOrderId(UUID orderId) { return Optional.ofNullable(byOrder.get(orderId)); }
}
