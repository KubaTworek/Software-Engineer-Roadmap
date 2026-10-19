package com.example.observability.server.infrastructure;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/** Pokazuje at-least-once: brak commitu oznacza redelivery, a klucz zachowuje kolejność. */
@Testcontainers(disabledWithoutDocker = true)
class KafkaDeliveryInfrastructureTest {
    @Container
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka-native:3.8.0");

    @Test
    void sameKeyIsOrderedAndUncommittedBatchIsRedelivered() throws Exception {
        String topic = "telemetry.contract";
        try (var admin = AdminClient.create(Map.of("bootstrap.servers", KAFKA.getBootstrapServers()))) {
            admin.createTopics(List.of(new NewTopic(topic, 1, (short) 1))).all().get();
        }
        var producerProperties = new Properties();
        producerProperties.put("bootstrap.servers", KAFKA.getBootstrapServers());
        producerProperties.put("key.serializer", StringSerializer.class.getName());
        producerProperties.put("value.serializer", StringSerializer.class.getName());
        try (var producer = new KafkaProducer<String, String>(producerProperties)) {
            producer.send(new ProducerRecord<>(topic, "tenant-a", "first")).get();
            producer.send(new ProducerRecord<>(topic, "tenant-a", "second")).get();
        }

        assertThat(readTwoWithoutCommit(topic)).containsExactly("first", "second");
        assertThat(readTwoWithoutCommit(topic)).containsExactly("first", "second");
    }

    private List<String> readTwoWithoutCommit(String topic) {
        var properties = new Properties();
        properties.put("bootstrap.servers", KAFKA.getBootstrapServers());
        properties.put("group.id", "redelivery-contract");
        properties.put("enable.auto.commit", "false");
        properties.put("auto.offset.reset", "earliest");
        properties.put("key.deserializer", StringDeserializer.class.getName());
        properties.put("value.deserializer", StringDeserializer.class.getName());
        try (var consumer = new KafkaConsumer<String, String>(properties)) {
            consumer.subscribe(List.of(topic));
            var values = new java.util.ArrayList<String>();
            long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
            while (values.size() < 2 && System.nanoTime() < deadline) {
                consumer.poll(Duration.ofMillis(250)).forEach(record -> values.add(record.value()));
            }
            return values;
        }
    }
}
