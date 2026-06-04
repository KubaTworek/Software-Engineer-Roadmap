package pl.jakubtworek.marketplace.integration.kafka.infrastructure;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import pl.jakubtworek.marketplace.integration.kafka.IntegrationEventEnvelope;
import pl.jakubtworek.marketplace.integration.kafka.KafkaMessageBroker;
import pl.jakubtworek.marketplace.integration.kafka.KafkaMessagePublisher;
import pl.jakubtworek.marketplace.integration.kafka.KafkaRecord;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Adapter do prawdziwej Kafki.
 *
 * Ta klasa zastępuje InMemoryKafkaBroker, gdy aplikacja działa z profilem kafka.
 *
 * Implementuje dwa porty:
 * - KafkaMessagePublisher — publikowanie wiadomości do topiców,
 * - KafkaMessageBroker — poll, commit, committedOffset, endOffset.
 *
 * Uwaga:
 * To nadal jest uproszczony adapter edukacyjny. W produkcyjnej aplikacji konsumowanie
 * wiadomości zwykle realizowałbym przez @KafkaListener z manualnym ack, a nie przez
 * ręczne tworzenie KafkaConsumer w metodzie poll(...).
 */
@Profile("kafka")
@Component
public class SpringKafkaMessageBroker implements KafkaMessageBroker, KafkaMessagePublisher {

    private final KafkaTemplate<String, IntegrationEventEnvelope> kafkaTemplate;
    private final String bootstrapServers;
    private final Duration pollTimeout;

    public SpringKafkaMessageBroker(
            KafkaTemplate<String, IntegrationEventEnvelope> kafkaTemplate,
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
            @Value("${marketplace.kafka.poll-timeout-ms:1000}") long pollTimeoutMs
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.bootstrapServers = bootstrapServers;
        this.pollTimeout = Duration.ofMillis(pollTimeoutMs);
    }

    /**
     * Publikuje wiadomość do prawdziwej Kafki.
     *
     * key powinien być stabilnym kluczem partycjonowania, np. orderId.
     * Dzięki temu eventy dotyczące jednego zamówienia trafiają do tej samej partycji
     * i Kafka zachowuje ich kolejność w ramach tej partycji.
     */
    @Override
    public void publish(String topic, String key, IntegrationEventEnvelope envelope) {
        kafkaTemplate.send(topic, key, envelope).join();
    }

    /**
     * Pobiera wiadomości z prawdziwej Kafki.
     *
     * Ten wariant ręcznie przypisuje konsumenta do partycji topicu i czyta od offsetu
     * następującego po ostatnio zatwierdzonym offsecie.
     *
     * Dzięki temu zachowujemy semantykę podobną do InMemoryKafkaBroker:
     * - committedOffset(...) zwraca ostatni przetworzony offset,
     * - poll(...) zwraca rekordy o offsetach większych niż committed.
     */
    @Override
    public List<KafkaRecord> poll(String topic, String consumerGroup, int maxRecords) {
        try (KafkaConsumer<String, IntegrationEventEnvelope> consumer = newConsumer(consumerGroup)) {
            List<TopicPartition> partitions = partitionsFor(topic, consumer);

            if (partitions.isEmpty()) {
                return List.of();
            }

            consumer.assign(partitions);

            for (TopicPartition partition : partitions) {
                OffsetAndMetadata committed = consumer.committed(partition);

                if (committed == null) {
                    consumer.seek(partition, 0L);
                } else {
                    consumer.seek(partition, committed.offset());
                }
            }

            var records = consumer.poll(pollTimeout);

            List<KafkaRecord> result = new ArrayList<>();

            records.forEach(record -> {
                if (result.size() < maxRecords) {
                    result.add(new KafkaRecord(
                            record.topic(),
                            record.partition(),
                            record.key(),
                            record.value(),
                            record.offset()
                    ));
                }
            });

            return result;
        }
    }

    /**
     * Commituje offset konkretnego rekordu.
     *
     * Kafka zapisuje offset następnej wiadomości do przeczytania, dlatego commitujemy:
     * offset + 1.
     *
     * Przykład:
     * - przetworzyliśmy rekord o offset 7,
     * - commit do Kafki powinien wynosić 8.
     */
    @Override
    public void commit(String topic, String consumerGroup, long offset) {
        /*
         * Ten wariant zakłada jedną partycję — partition 0.
         *
         * Jeśli używasz wielu partycji, interfejs KafkaMessageBroker powinien zostać
         * rozszerzony o partition, np. commit(topic, consumerGroup, partition, offset).
         */
        commit(topic, consumerGroup, 0, offset);
    }

    public void commit(String topic, String consumerGroup, int partition, long offset) {
        try (KafkaConsumer<String, IntegrationEventEnvelope> consumer = newConsumer(consumerGroup)) {
            TopicPartition topicPartition = new TopicPartition(topic, partition);

            consumer.assign(List.of(topicPartition));

            consumer.commitSync(Map.of(
                    topicPartition,
                    new OffsetAndMetadata(offset + 1)
            ));
        }
    }

    /**
     * Zwraca ostatni zatwierdzony offset.
     *
     * Kafka przechowuje offset następnego rekordu do przeczytania.
     * Dla spójności z InMemoryKafkaBroker odejmujemy 1.
     *
     * Jeśli grupa nie ma jeszcze commitu, zwracamy -1.
     */
    @Override
    public long committedOffset(String topic, String consumerGroup) {
        /*
         * Ten wariant zakłada jedną partycję — partition 0.
         */
        return committedOffset(topic, consumerGroup, 0);
    }

    public long committedOffset(String topic, String consumerGroup, int partition) {
        try (KafkaConsumer<String, IntegrationEventEnvelope> consumer = newConsumer(consumerGroup)) {
            TopicPartition topicPartition = new TopicPartition(topic, partition);

            consumer.assign(List.of(topicPartition));

            OffsetAndMetadata committed = consumer.committed(topicPartition);

            if (committed == null) {
                return -1L;
            }

            return committed.offset() - 1;
        }
    }

    /**
     * Zwraca ostatni offset w topicu.
     *
     * Kafka endOffset oznacza offset następnego rekordu, więc dla spójności z Twoim
     * in-memory brokerem odejmujemy 1.
     */
    @Override
    public long endOffset(String topic) {
        try (KafkaConsumer<String, IntegrationEventEnvelope> consumer = newConsumer("marketplace-end-offset-reader")) {
            List<TopicPartition> partitions = partitionsFor(topic, consumer);

            if (partitions.isEmpty()) {
                return -1L;
            }

            consumer.assign(partitions);

            Map<TopicPartition, Long> endOffsets = consumer.endOffsets(partitions);

            return endOffsets.values().stream()
                    .mapToLong(endOffset -> endOffset - 1)
                    .max()
                    .orElse(-1L);
        }
    }

    private KafkaConsumer<String, IntegrationEventEnvelope> newConsumer(String consumerGroup) {
        return new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG, consumerGroup,
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, org.apache.kafka.common.serialization.StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, org.springframework.kafka.support.serializer.JsonDeserializer.class,
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                org.springframework.kafka.support.serializer.JsonDeserializer.TRUSTED_PACKAGES, "*",
                org.springframework.kafka.support.serializer.JsonDeserializer.VALUE_DEFAULT_TYPE, IntegrationEventEnvelope.class.getName()
        ));
    }

    private List<TopicPartition> partitionsFor(
            String topic,
            KafkaConsumer<String, IntegrationEventEnvelope> consumer
    ) {
        var partitions = consumer.partitionsFor(topic);

        if (partitions == null || partitions.isEmpty()) {
            return List.of();
        }

        return partitions.stream()
                .map(info -> new TopicPartition(topic, info.partition()))
                .toList();
    }
}