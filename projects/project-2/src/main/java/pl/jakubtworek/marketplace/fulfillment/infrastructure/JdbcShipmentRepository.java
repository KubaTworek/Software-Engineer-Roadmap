package pl.jakubtworek.marketplace.fulfillment.infrastructure;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import pl.jakubtworek.marketplace.fulfillment.application.ShipmentRepository;
import pl.jakubtworek.marketplace.fulfillment.domain.FulfillmentStatus;
import pl.jakubtworek.marketplace.fulfillment.domain.Shipment;
import java.util.Optional;
import java.util.UUID;

/** Unikalne order_id przenosi idempotencję handlera z check-then-act do PostgreSQL. */
@Repository
@Profile("postgres")
public class JdbcShipmentRepository implements ShipmentRepository {
    private final JdbcTemplate jdbc;
    public JdbcShipmentRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public Shipment saveIfAbsent(Shipment shipment) {
        jdbc.update("""
                INSERT INTO fulfillment.shipments(id, order_id, status)
                VALUES (?, ?, ?) ON CONFLICT (order_id) DO NOTHING
                """, shipment.id(), shipment.orderId(), shipment.status().name());
        return findByOrderId(shipment.orderId()).orElseThrow();
    }
    @Override public Optional<Shipment> findByOrderId(UUID orderId) {
        return jdbc.query("SELECT id, order_id, status FROM fulfillment.shipments WHERE order_id = ?",
                (rs, row) -> new Shipment(rs.getObject("id", UUID.class), rs.getObject("order_id", UUID.class),
                        FulfillmentStatus.valueOf(rs.getString("status"))), orderId).stream().findFirst();
    }
}
