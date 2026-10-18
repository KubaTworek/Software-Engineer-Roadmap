package pl.jakubtworek.marketplace.integration.kafka;

import java.util.List;

public interface KafkaMessageBroker extends KafkaMessagePublisher {
    List<KafkaRecord> poll(String topic, String consumerGroup, int maxRecords);

    /**
     * Zatwierdza rekord w jego rzeczywistej partycji.
     *
     * Offset nie jest globalny dla topicu. Para {@code (partition, offset)} jest
     * dopiero pełną pozycją rekordu, dlatego pominięcie partycji może zatwierdzić
     * niewłaściwy rekord albo pozostawić poprawnie obsłużoną partycję bez commitu.
     */
    void commit(String topic, String consumerGroup, int partition, long offset);

    long committedOffset(String topic, String consumerGroup, int partition);

    /**
     * Skróty zachowują czytelność prostych laboratoriów jednopartycyjnych.
     * Kod produkcyjnej ścieżki konsumenta zawsze używa wariantu z partycją.
     */
    default void commit(String topic, String consumerGroup, long offset) {
        commit(topic, consumerGroup, 0, offset);
    }

    default long committedOffset(String topic, String consumerGroup) {
        return committedOffset(topic, consumerGroup, 0);
    }

    default long endOffset(String topic) { return -1L; }

    default long lag(String topic, String consumerGroup) {
        return Math.max(0, endOffset(topic) - committedOffset(topic, consumerGroup));
    }
}
