package com.example.urlshortener.queue;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class RabbitMqConfig {

    @Bean
    Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, Jackson2JsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        template.setMandatory(true);
        return template;
    }

    @Bean
    SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
        ConnectionFactory connectionFactory,
        Jackson2JsonMessageConverter converter
    ) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(converter);
        factory.setConcurrentConsumers(2);
        factory.setMaxConcurrentConsumers(8);
        factory.setDefaultRequeueRejected(false);
        return factory;
    }

    @Bean
    DirectExchange clickExchange(QueueProperties properties) {
        return new DirectExchange(properties.clickExchange(), true, false);
    }

    @Bean
    DirectExchange clickDeadLetterExchange(QueueProperties properties) {
        return new DirectExchange(properties.clickDeadLetterExchange(), true, false);
    }

    @Bean
    Queue clickQueue(QueueProperties properties) {
        return new Queue(properties.clickQueue(), true, false, false, Map.of(
            "x-dead-letter-exchange", properties.clickDeadLetterExchange(),
            "x-dead-letter-routing-key", "click.dead"
        ));
    }

    @Bean
    Queue clickDeadLetterQueue(QueueProperties properties) {
        return new Queue(properties.clickDeadLetterQueue(), true);
    }

    @Bean
    Binding clickBinding(QueueProperties properties, Queue clickQueue, DirectExchange clickExchange) {
        return BindingBuilder.bind(clickQueue).to(clickExchange).with(properties.clickRoutingKey());
    }

    @Bean
    Binding clickDlqBinding(QueueProperties properties, Queue clickDeadLetterQueue, DirectExchange clickDeadLetterExchange) {
        return BindingBuilder.bind(clickDeadLetterQueue).to(clickDeadLetterExchange).with("click.dead");
    }
}
