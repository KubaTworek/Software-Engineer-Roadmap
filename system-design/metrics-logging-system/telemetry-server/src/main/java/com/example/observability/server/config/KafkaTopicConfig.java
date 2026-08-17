package com.example.observability.server.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

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
}
