package pl.jakubtworek.marketplace.inventory.application;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import pl.jakubtworek.marketplace.integration.contracts.StockReleaseRequestedIntegrationEvent;
import pl.jakubtworek.marketplace.shared.events.DomainEventHandler;
import pl.jakubtworek.marketplace.shared.events.EventPublisher;
import pl.jakubtworek.marketplace.inventory.domain.StockReleased;

/**
 * Zwalnia wszystkie aktywne linie zamówienia w jednej lokalnej transakcji.
 * Atomowe oznaczenie rekordu jako released sprawia, że duplikat nie zwiększy stocku.
 */
@Component
public class ReleaseStockHandler implements DomainEventHandler<StockReleaseRequestedIntegrationEvent> {
    private final StockRepository stock;
    private final StockReservationRepository reservations;
    private final EventPublisher events;
    @Autowired
    public ReleaseStockHandler(StockRepository stock, StockReservationRepository reservations, EventPublisher events) {
        this.stock = stock; this.reservations = reservations; this.events = events;
    }
    public ReleaseStockHandler(StockRepository stock, StockReservationRepository reservations) {
        this(stock, reservations, ignored -> { });
    }
    @Override public Class<StockReleaseRequestedIntegrationEvent> eventType() {
        return StockReleaseRequestedIntegrationEvent.class;
    }
    @Override @Transactional
    public void handle(StockReleaseRequestedIntegrationEvent event) {
        var lines = reservations.findActive(event.orderId());
        if (lines.isEmpty()) return;
        // Claim wykonujemy przed zmianą stocku. W JDBC update i poniższe zapisy są
        // jedną transakcją: rollback cofa claim, a konkurencyjny duplikat dostaje false.
        if (!reservations.markReleased(event.orderId())) return;
        for (var line : lines) {
            var item = stock.findByProductId(line.productId()).orElseThrow();
            item.releaseWithoutPublishingEvent(line.quantity());
            stock.save(item);
        }
        events.publish(StockReleased.now(event.orderId(), event.correlationId(), event.eventId()));
    }
}
