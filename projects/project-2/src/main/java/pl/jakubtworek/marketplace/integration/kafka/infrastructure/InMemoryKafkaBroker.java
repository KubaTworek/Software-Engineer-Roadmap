package pl.jakubtworek.marketplace.integration.kafka.infrastructure;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import pl.jakubtworek.marketplace.integration.kafka.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Profile("!kafka")
@Component
public class InMemoryKafkaBroker implements KafkaMessageBroker {
    private final Map<String, List<KafkaRecord>> records = new ConcurrentHashMap<>();
    private final Map<String, Long> committedOffsets = new ConcurrentHashMap<>();

    @Override
    public void publish(String topic, String key, IntegrationEventEnvelope envelope) {
        var topicRecords = records.computeIfAbsent(topic, ignored -> new CopyOnWriteArrayList<>());
        topicRecords.add(new KafkaRecord(topic, key, envelope, topicRecords.size()));
    }

    @Override
    public List<KafkaRecord> poll(String topic, String consumerGroup, int maxRecords) {
        long committed = committedOffset(topic, consumerGroup);
        return records.getOrDefault(topic, List.of()).stream()
                .filter(record -> record.offset() > committed)
                .limit(maxRecords)
                .toList();
    }

    @Override
    public void commit(String topic, String consumerGroup, long offset) {
        committedOffsets.merge(key(topic, consumerGroup), offset, Math::max);
    }

    @Override
    public long committedOffset(String topic, String consumerGroup) {
        return committedOffsets.getOrDefault(key(topic, consumerGroup), -1L);
    }

    @Override
    public long endOffset(String topic) {
        return records.getOrDefault(topic, List.of()).size() - 1L;
    }

    public long lag(String topic, String consumerGroup) {
        long end = endOffset(topic);
        long committed = committedOffset(topic, consumerGroup);
        return Math.max(0, end - committed);
    }

    public List<KafkaRecord> records(String topic) {
        return List.copyOf(records.getOrDefault(topic, List.of()));
    }

    private String key(String topic, String consumerGroup) {
        return topic + "::" + consumerGroup;
    }
}
