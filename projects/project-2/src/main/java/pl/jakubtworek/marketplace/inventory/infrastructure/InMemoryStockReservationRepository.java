package pl.jakubtworek.marketplace.inventory.infrastructure;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import pl.jakubtworek.marketplace.inventory.application.StockReservationRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository @Profile("!postgres")
public class InMemoryStockReservationRepository implements StockReservationRepository {
    private final Map<UUID, Reservation> reservations = new ConcurrentHashMap<>();
    @Override public void record(UUID orderId, List<Line> lines) {
        reservations.putIfAbsent(orderId, new Reservation(List.copyOf(lines), false));
    }
    @Override public List<Line> findActive(UUID orderId) {
        Reservation value = reservations.get(orderId);
        return value == null || value.released ? List.of() : value.lines;
    }
    @Override public synchronized boolean markReleased(UUID orderId) {
        Reservation current = reservations.get(orderId);
        if (current == null || current.released) return false;
        reservations.put(orderId, new Reservation(current.lines, true));
        return true;
    }
    private record Reservation(List<Line> lines, boolean released) {}
}
