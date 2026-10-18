package com.example.filestorage.infrastructure;

import com.example.filestorage.production.outbox.DomainEventPublisher;
import com.example.filestorage.production.outbox.KafkaDomainEventPublisher;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Kontrakt z prawdziwa Kafka: klucz agregatu i eventId przechodza przez broker. */
@Testcontainers
class KafkaOutboxInfrastructureTest {
    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(
            DockerImageName.parse("apache/kafka-native:3.8.0"));

    @Test
    void publishesStableIdentityAndAggregateOrderingKey() throws Exception {
        String topic = "file-storage.events.test";
        Map<String, Object> adminConfig = Map.of(
                org.apache.kafka.clients.admin.AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,
                KAFKA.getBootstrapServers());
        try (AdminClient admin = AdminClient.create(adminConfig)) {
            admin.createTopics(List.of(new NewTopic(topic, 1, (short) 1))).all().get();
        }

        var producerFactory = new DefaultKafkaProducerFactory<String, String>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.ACKS_CONFIG, "all"));
        KafkaTemplate<String, String> template = new KafkaTemplate<>(producerFactory);
        var publisher = new KafkaDomainEventPublisher(template, new ObjectMapper().findAndRegisterModules(), topic);
        UUID eventId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        publisher.publish(new DomainEventPublisher.OutboxEnvelope(
                eventId, "FILE_AVAILABLE", "FILE", fileId, "{}"));

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "contract-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class))) {
            consumer.subscribe(List.of(topic));
            var records = consumer.poll(Duration.ofSeconds(10));
            assertThat(records).hasSize(1);
            var record = records.iterator().next();
            assertThat(record.key()).isEqualTo("FILE:" + fileId);
            assertThat(new String(record.headers().lastHeader("eventId").value(), java.nio.charset.StandardCharsets.UTF_8))
                    .isEqualTo(eventId.toString());
            assertThat(record.value()).contains("FILE_AVAILABLE", fileId.toString());
        } finally {
            template.destroy();
            producerFactory.destroy();
        }
    }
}
