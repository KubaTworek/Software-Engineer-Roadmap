package pl.jakubtworek.chatsystem.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.*;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class KafkaEventBusIT {
    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka-native:3.9.1"));

    @Test
    void sameConversationUsesOnePartitionAndUncommittedRecordIsRedelivered() throws Exception {
        String topic = "chat-domain-events";
        Map<String, Object> producerProperties = Map.of(
                "bootstrap.servers", KAFKA.getBootstrapServers(),
                "key.serializer", StringSerializer.class,
                "value.serializer", StringSerializer.class,
                "acks", "all");
        DefaultKafkaProducerFactory<String, String> producerFactory =
                new DefaultKafkaProducerFactory<>(producerProperties);
        KafkaTemplate<String, String> template = new KafkaTemplate<>(producerFactory);
        ObjectMapper json = new ObjectMapper().findAndRegisterModules();
        KafkaEventBus bus = new KafkaEventBus(template, json, topic);
        UUID conversationId = UUID.randomUUID();
        OutboxEvent first = new OutboxEvent(UUID.randomUUID(), "MESSAGE_CREATED",
                "{\"conversationId\":\"" + conversationId + "\",\"sequence\":1}");
        OutboxEvent second = new OutboxEvent(UUID.randomUUID(), "MESSAGE_CREATED",
                "{\"conversationId\":\"" + conversationId + "\",\"sequence\":2}");
        bus.enqueue(first);
        bus.enqueue(second);

        List<ConsumerRecord<String, String>> initial = consume(topic, "delivery-proof", 2);
        assertThat(initial).extracting(ConsumerRecord::key)
                .containsOnly(conversationId.toString());
        assertThat(initial).extracting(ConsumerRecord::partition).containsOnly(initial.getFirst().partition());
        assertThat(initial).extracting(record -> json.readTree(record.value()).get("eventId").asText())
                .containsExactly(first.getId().toString(), second.getId().toString());

        // Pierwszy consumer zamknął się bez commitSync. Ten sam consumer group musi
        // ponownie zobaczyć rekord: broker gwarantuje at-least-once, nie exactly-once handlera.
        List<ConsumerRecord<String, String>> redelivered = consume(topic, "delivery-proof", 1);
        assertThat(redelivered.getFirst().value()).isEqualTo(initial.getFirst().value());
        template.destroy();
        producerFactory.destroy();
    }

    private List<ConsumerRecord<String, String>> consume(String topic, String group, int expected) {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, group);
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        List<ConsumerRecord<String, String>> result = new ArrayList<>();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties)) {
            consumer.subscribe(List.of(topic));
            long deadline = System.nanoTime() + Duration.ofSeconds(15).toNanos();
            while (result.size() < expected && System.nanoTime() < deadline) {
                consumer.poll(Duration.ofMillis(250)).forEach(result::add);
            }
        }
        assertThat(result).hasSizeGreaterThanOrEqualTo(expected);
        return result.subList(0, expected);
    }
}
