package pl.jakubtworek.backend.notification.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import pl.jakubtworek.backend.common.events.OrderPaidEvent;
import pl.jakubtworek.backend.notification.config.RabbitConfig;

@Component
public class NotificationListener {
    private static final Logger log = LoggerFactory.getLogger(NotificationListener.class);
    private final long processingDelayMs;

    public NotificationListener(@Value("${notification.processing-delay-ms:0}") long processingDelayMs) {
        this.processingDelayMs = processingDelayMs;
    }

    @RabbitListener(queues = RabbitConfig.NOTIFICATIONS_QUEUE)
    public void onOrderPaid(OrderPaidEvent event) throws InterruptedException {
        if (processingDelayMs > 0) {
            Thread.sleep(processingDelayMs);
        }
        log.info("Sending notification for paid order: orderId={}, reservationId={}, eventId={}, userId={}, amount={}",
                event.orderId(), event.reservationId(), event.eventId(), event.userId(), event.amount());
    }
}
