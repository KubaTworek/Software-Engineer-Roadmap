package pl.jakubtworek.marketplace.integration.contracts;

import pl.jakubtworek.marketplace.shared.kernel.DomainEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Kanoniczny, konsumencki model zdarzenia {@code OrderPlaced}.
 *
 * <p>Nie jest to agregat ani zdarzenie domenowe modułu Ordering. To opublikowany
 * język integracji: stabilny kształt, na który historyczne wersje komunikatu są
 * mapowane na granicy systemu. Dzięki temu Payment i Inventory nie importują
 * modelu domenowego właściciela zamówienia.</p>
 */
public record OrderPlacedIntegrationEvent(
        UUID eventId,
        UUID aggregateId,
        UUID customerId,
        MoneyValue total,
        List<Line> lines,
        Instant occurredAt,
        UUID correlationId,
        UUID causationId
) implements DomainEvent {
    public OrderPlacedIntegrationEvent {
        lines = lines == null ? List.of() : List.copyOf(lines);
    }

    public record MoneyValue(BigDecimal amount, String currency) {}
    public record Line(UUID productId, int quantity, MoneyValue unitPrice) {}

    @Override public String eventType() { return "OrderPlaced"; }
    @Override public int eventVersion() { return 2; }
}
