package pl.jakubtworek.marketplace.inventory.infrastructure;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import pl.jakubtworek.marketplace.inventory.application.StockReservationRepository;
import java.util.List;
import java.util.UUID;

@Repository @Profile("postgres")
public class JdbcStockReservationRepository implements StockReservationRepository {
    private final JdbcTemplate jdbc;
    public JdbcStockReservationRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public void record(UUID orderId, List<Line> lines) {
        lines.forEach(line -> jdbc.update("""
                INSERT INTO inventory.stock_reservations(order_id, product_id, quantity, released)
                VALUES (?, ?, ?, false) ON CONFLICT (order_id, product_id) DO NOTHING
                """, orderId, line.productId(), line.quantity()));
    }
    @Override public List<Line> findActive(UUID orderId) {
        return jdbc.query("""
                SELECT product_id, quantity FROM inventory.stock_reservations
                WHERE order_id = ? AND released = false ORDER BY product_id
                """, (rs, row) -> new Line(rs.getObject(1, UUID.class), rs.getInt(2)), orderId);
    }
    @Override public boolean markReleased(UUID orderId) {
        return jdbc.update("""
                UPDATE inventory.stock_reservations SET released = true
                WHERE order_id = ? AND released = false
                """, orderId) > 0;
    }
}
