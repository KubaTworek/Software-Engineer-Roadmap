package com.example.newsfeed.config;

import com.example.newsfeed.events.DomainEvent;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

@EnableKafka
@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic postCreatedTopic() {
        return TopicBuilder.name("newsfeed.post.created").partitions(6).replicas(1).build();
    }

    @Bean
    public NewTopic postDeletedTopic() {
        return TopicBuilder.name("newsfeed.post.deleted").partitions(6).replicas(1).build();
    }

    @Bean
    public NewTopic statsTopic() {
        return TopicBuilder.name("newsfeed.stats").partitions(6).replicas(1).build();
    }

    @Bean
    public NewTopic dlqTopic() {
        return TopicBuilder.name("newsfeed.dlq").partitions(3).replicas(1).build();
    }

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaOperations<String, DomainEvent> kafkaOperations) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaOperations,
                (record, exception) -> new TopicPartition("newsfeed.dlq", record.partition())
        );

        ExponentialBackOff backOff = new ExponentialBackOff(250L, 2.0);
        backOff.setMaxElapsedTime(10_000L);

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(IllegalArgumentException.class);
        return handler;
    }
}
