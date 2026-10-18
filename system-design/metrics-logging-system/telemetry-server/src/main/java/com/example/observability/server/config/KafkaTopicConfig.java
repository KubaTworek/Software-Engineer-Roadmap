package com.example.observability.server.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;
import org.apache.kafka.common.TopicPartition;

@Configuration
public class KafkaTopicConfig {
    @Bean
    NewTopic logsTopic(@Value("${telemetry.kafka.logs-topic}") String topic) {
        return TopicBuilder.name(topic).partitions(6).replicas(1).build();
    }

    @Bean
    NewTopic metricsTopic(@Value("${telemetry.kafka.metrics-topic}") String topic) {
        return TopicBuilder.name(topic).partitions(6).replicas(1).build();
    }

    @Bean
    NewTopic logsDeadLetterTopic(
            @Value("${telemetry.kafka.logs-topic}") String topic,
            @Value("${telemetry.kafka.dead-letter-suffix}") String suffix) {
        return TopicBuilder.name(topic + suffix).partitions(6).replicas(1).build();
    }

    @Bean
    NewTopic metricsDeadLetterTopic(
            @Value("${telemetry.kafka.metrics-topic}") String topic,
            @Value("${telemetry.kafka.dead-letter-suffix}") String suffix) {
        return TopicBuilder.name(topic + suffix).partitions(6).replicas(1).build();
    }

    /**
     * Trzy ograniczone ponowienia chronią przed chwilową awarią ClickHouse.
     * Rekord trwale wadliwy trafia do topicu `<source>.DLT` razem z nagłówkami
     * opisującymi wyjątek; nie blokuje więc całej partycji bez końca.
     */
    @Bean
    CommonErrorHandler telemetryKafkaErrorHandler(
            KafkaTemplate<Object, Object> template,
            @Value("${telemetry.kafka.dead-letter-suffix}") String suffix) {
        var recoverer = new DeadLetterPublishingRecoverer(template,
                (record, exception) -> new TopicPartition(record.topic() + suffix, record.partition()));
        var handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1_000, 3));
        handler.setCommitRecovered(true);
        return handler;
    }
}
