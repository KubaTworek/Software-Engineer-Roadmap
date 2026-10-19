package pl.jakubtworek.backend.notification.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Konfiguracja RabbitMQ dla Notification Service.
 *
 * Ten serwis subskrybuje eventy związane z zamówieniami i reaguje na event:
 *
 * order.paid
 *
 * Przepływ:
 *
 * order-service
 *   -> orders.exchange
 *      -> routing key: order.paid
 *         -> notifications.order-paid queue
 *            -> notification-service
 *
 * Dzięki temu Order Service nie musi znać Notification Service bezpośrednio.
 * To zmniejsza coupling między serwisami i pozwala notification-service działać
 * asynchronicznie.
 */
@Configuration
public class RabbitConfig {

    /**
     * Nazwa exchange'a, do którego Order Service publikuje eventy zamówień.
     *
     * Exchange jest logicznym punktem wejścia dla wiadomości.
     * Producent publikuje do exchange'a, a RabbitMQ decyduje, do których kolejek
     * trafi wiadomość na podstawie bindingów i routing key.
     */
    public static final String ORDERS_EXCHANGE = "orders.exchange";

    /**
     * Routing key dla eventu oznaczającego opłacenie zamówienia.
     *
     * Ten routing key musi być spójny z tym, czego używa Order Service przy publikacji.
     */
    public static final String ORDER_PAID_ROUTING_KEY = "order.paid";

    /**
     * Kolejka konsumowana przez Notification Service.
     *
     * Nazwa sugeruje konkretny use case: powiadomienia po opłaceniu zamówienia.
     */
    public static final String NOTIFICATIONS_QUEUE = "notifications.order-paid";
    public static final String NOTIFICATIONS_DLQ = "notifications.order-paid.dlq";
    public static final String DEAD_LETTER_EXCHANGE = "notifications.dlx";

    /**
     * Tworzy durable TopicExchange.
     *
     * Parametry:
     *
     * - name: orders.exchange
     * - durable: true
     * - autoDelete: false
     *
     * durable = true oznacza, że exchange przetrwa restart RabbitMQ.
     * autoDelete = false oznacza, że exchange nie zostanie automatycznie usunięty,
     * gdy nie będzie już używany.
     *
     * TopicExchange pozwala routować wiadomości po wzorcach routing key, np.:
     *
     * order.paid
     * order.failed
     * order.*
     *
     * W tym projekcie używamy konkretnego routing key order.paid, ale TopicExchange
     * zostawia miejsce na rozwój.
     */
    @Bean
    TopicExchange ordersExchange() {
        return new TopicExchange(ORDERS_EXCHANGE, true, false);
    }

    /**
     * Tworzy durable queue dla Notification Service.
     *
     * durable queue przetrwa restart brokera. To jest ważne, bo nie chcemy utracić
     * eventów tylko dlatego, że RabbitMQ został zrestartowany.
     *
     * Uwaga:
     * Durable queue nie wystarczy do pełnej trwałości wiadomości. Producent powinien
     * publikować wiadomości jako persistent, jeśli zależy nam na przetrwaniu restartu brokera.
     */
    @Bean
    Queue notificationsQueue() {
        return QueueBuilder
                .durable(NOTIFICATIONS_QUEUE)
                .deadLetterExchange(DEAD_LETTER_EXCHANGE)
                .deadLetterRoutingKey(NOTIFICATIONS_DLQ)
                .build();
    }

    /**
     * Poison message nie może blokować głównej kolejki w nieskończoność. DLQ zachowuje
     * wiadomość i nagłówki x-death, dzięki czemu operator może zdiagnozować przyczynę,
     * poprawić konsumenta i wykonać kontrolowany replay.
     */
    @Bean DirectExchange deadLetterExchange() { return new DirectExchange(DEAD_LETTER_EXCHANGE, true, false); }
    @Bean Queue notificationsDeadLetterQueue() { return QueueBuilder.durable(NOTIFICATIONS_DLQ).build(); }
    @Bean Binding deadLetterBinding(Queue notificationsDeadLetterQueue, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(notificationsDeadLetterQueue).to(deadLetterExchange).with(NOTIFICATIONS_DLQ);
    }

    /**
     * Łączy kolejkę notification-service z exchange'em orders.exchange.
     *
     * Binding mówi RabbitMQ:
     *
     * "Jeśli do orders.exchange trafi wiadomość z routing key order.paid,
     *  przekaż ją do kolejki notifications.order-paid".
     */
    @Bean
    Binding binding(Queue notificationsQueue, TopicExchange ordersExchange) {
        return BindingBuilder
                .bind(notificationsQueue)
                .to(ordersExchange)
                .with(ORDER_PAID_ROUTING_KEY);
    }

    /**
     * Konwerter wiadomości RabbitMQ <-> Java object.
     *
     * JacksonJsonMessageConverter pozwala automatycznie serializować i deserializować
     * eventy jako JSON.
     *
     * Dzięki temu listener może przyjmować np. OrderPaidEvent jako obiekt Java,
     * zamiast ręcznie parsować String/byte[].
     */
    @Bean
    MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
