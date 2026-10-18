package pl.jakubtworek.chatsystem.outbox;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Każdy handler ma osobną transakcję inboxa. Awaria późniejszego konsumenta nie usuwa dowodu,
 * że wcześniejszy już wykonał efekt. Nadal istnieje klasyczne okno crash między efektem zewnętrznym
 * a zapisem inboxa, dlatego adapter providera/realtime musi przyjmować stabilny eventId.
 */
@Service
public class OutboxHandlerExecutor {
    private final OutboxHandlerDeliveryRepository deliveries;

    public OutboxHandlerExecutor(OutboxHandlerDeliveryRepository deliveries) { this.deliveries = deliveries; }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void executeOnce(OutboxEvent event, DomainEventHandler handler) {
        String handlerName = handler.consumerName();
        if (deliveries.existsByEventIdAndHandlerName(event.getId(), handlerName)) return;
        handler.handle(event);
        deliveries.save(new OutboxHandlerDelivery(event.getId(), handlerName));
    }
}
