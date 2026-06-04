package pl.jakubtworek.backend.notification.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {
    public static final String ORDERS_EXCHANGE = "orders.exchange";
    public static final String ORDER_PAID_ROUTING_KEY = "order.paid";
    public static final String NOTIFICATIONS_QUEUE = "notifications.order-paid";

    @Bean
    TopicExchange ordersExchange() {
        return new TopicExchange(ORDERS_EXCHANGE, true, false);
    }

    @Bean
    Queue notificationsQueue() {
        return QueueBuilder.durable(NOTIFICATIONS_QUEUE).build();
    }

    @Bean
    Binding binding(Queue notificationsQueue, TopicExchange ordersExchange) {
        return BindingBuilder.bind(notificationsQueue).to(ordersExchange).with(ORDER_PAID_ROUTING_KEY);
    }

    @Bean
    MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
