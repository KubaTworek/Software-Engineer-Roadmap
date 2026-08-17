package com.example.videostreaming.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {
    @Bean
    DirectExchange videoExchange(MessagingProperties props) {
        return ExchangeBuilder.directExchange(props.exchange()).durable(true).build();
    }

    @Bean
    Queue transcodingQueue(MessagingProperties props) {
        return QueueBuilder.durable(props.transcodingQueue())
                .withArgument("x-dead-letter-exchange", props.exchange())
                .withArgument("x-dead-letter-routing-key", props.transcodingDlqRoutingKey())
                .build();
    }

    @Bean
    Queue transcodingDlq(MessagingProperties props) {
        return QueueBuilder.durable(props.transcodingDlq()).build();
    }

    @Bean
    Queue qoeQueue(MessagingProperties props) {
        return QueueBuilder.durable(props.qoeQueue())
                .withArgument("x-dead-letter-exchange", props.exchange())
                .withArgument("x-dead-letter-routing-key", props.qoeDlqRoutingKey())
                .build();
    }

    @Bean
    Queue qoeDlq(MessagingProperties props) {
        return QueueBuilder.durable(props.qoeDlq()).build();
    }

    @Bean
    Queue liveStartQueue(MessagingProperties props) {
        return QueueBuilder.durable(props.liveStartQueue())
                .withArgument("x-dead-letter-exchange", props.exchange())
                .withArgument("x-dead-letter-routing-key", props.liveDlqRoutingKey())
                .build();
    }

    @Bean
    Queue liveStopQueue(MessagingProperties props) {
        return QueueBuilder.durable(props.liveStopQueue())
                .withArgument("x-dead-letter-exchange", props.exchange())
                .withArgument("x-dead-letter-routing-key", props.liveDlqRoutingKey())
                .build();
    }

    @Bean
    Queue liveDlq(MessagingProperties props) {
        return QueueBuilder.durable(props.liveDlq()).build();
    }

    @Bean
    Binding transcodingBinding(DirectExchange videoExchange, Queue transcodingQueue, MessagingProperties props) {
        return BindingBuilder.bind(transcodingQueue).to(videoExchange).with(props.transcodingRoutingKey());
    }

    @Bean
    Binding transcodingDlqBinding(DirectExchange videoExchange, Queue transcodingDlq, MessagingProperties props) {
        return BindingBuilder.bind(transcodingDlq).to(videoExchange).with(props.transcodingDlqRoutingKey());
    }

    @Bean
    Binding qoeBinding(DirectExchange videoExchange, Queue qoeQueue, MessagingProperties props) {
        return BindingBuilder.bind(qoeQueue).to(videoExchange).with(props.qoeRoutingKey());
    }

    @Bean
    Binding qoeDlqBinding(DirectExchange videoExchange, Queue qoeDlq, MessagingProperties props) {
        return BindingBuilder.bind(qoeDlq).to(videoExchange).with(props.qoeDlqRoutingKey());
    }

    @Bean
    Binding liveStartBinding(DirectExchange videoExchange, Queue liveStartQueue, MessagingProperties props) {
        return BindingBuilder.bind(liveStartQueue).to(videoExchange).with(props.liveStartRoutingKey());
    }

    @Bean
    Binding liveStopBinding(DirectExchange videoExchange, Queue liveStopQueue, MessagingProperties props) {
        return BindingBuilder.bind(liveStopQueue).to(videoExchange).with(props.liveStopRoutingKey());
    }

    @Bean
    Binding liveDlqBinding(DirectExchange videoExchange, Queue liveDlq, MessagingProperties props) {
        return BindingBuilder.bind(liveDlq).to(videoExchange).with(props.liveDlqRoutingKey());
    }

    @Bean
    Jackson2JsonMessageConverter jackson2JsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, Jackson2JsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        return template;
    }

    @Bean
    SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory,
                                                                       Jackson2JsonMessageConverter converter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(converter);
        factory.setDefaultRequeueRejected(false);
        factory.setConcurrentConsumers(1);
        factory.setMaxConcurrentConsumers(4);
        factory.setPrefetchCount(1);
        return factory;
    }
}
