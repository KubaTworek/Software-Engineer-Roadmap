package pl.jakubtworek.marketplace.inventory.application;

import java.util.List;
import java.util.UUID;

/**
 * Rejestr skutków rezerwacji należący do Inventory. Saga podaje jedynie orderId;
 * szczegóły stocku nie wyciekają do koordynatora procesu.
 */
public interface StockReservationRepository {
    void record(UUID orderId, List<Line> lines);
    List<Line> findActive(UUID orderId);
    boolean markReleased(UUID orderId);
    record Line(UUID productId, int quantity) {}
}
