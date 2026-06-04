package pl.jakubtworek.marketplace.integration.kafka.infrastructure;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import pl.jakubtworek.marketplace.integration.kafka.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory implementacja brokera Kafki.
 *
 * Ta klasa pełni dwie role:
 * - implementuje KafkaMessageBroker, czyli pozwala konsumentom pobierać rekordy,
 *   commitować offsety i sprawdzać lag,
 * - implementuje mechanizm publikacji wiadomości do topiców w pamięci procesu.
 *
 * Jest aktywna tylko wtedy, gdy profil kafka NIE jest włączony.
 *
 * Dzięki temu:
 * - w testach nie trzeba uruchamiać prawdziwej Kafki,
 * - lokalny flow event-driven można ćwiczyć bez brokera,
 * - KafkaOutboxWorker może publikować eventy do in-memory brokera,
 * - KafkaConsumerWorker może je później pobierać i przetwarzać.
 *
 * To nie jest produkcyjny broker.
 * Nie zapewnia partycji, replikacji, trwałości, realnych consumer groups ani zachowania
 * identycznego z Kafką. Służy wyłącznie do testów i lokalnych ćwiczeń.
 */
@Profile("!kafka")
@Component
public class InMemoryKafkaBroker implements KafkaMessageBroker, KafkaMessagePublisher {

    /**
     * Rekordy zapisane per topic.
     *
     * Klucz mapy to nazwa topicu, a wartość to lista rekordów w tym topicu.
     *
     * CopyOnWriteArrayList upraszcza bezpieczny odczyt/zapis w testach, ale nie jest
     * optymalna dla dużej liczby wiadomości. Tutaj jest wystarczająca, bo to broker testowy.
     */
    private final Map<String, List<KafkaRecord>> records = new ConcurrentHashMap<>();

    /**
     * Zapamiętane offsety per topic i consumer group.
     *
     * Klucz techniczny ma format:
     * topic::consumerGroup
     *
     * Wartość oznacza ostatni zatwierdzony offset.
     */
    private final Map<String, Long> committedOffsets = new ConcurrentHashMap<>();

    /**
     * Publikuje wiadomość do wskazanego topicu.
     *
     * Offset jest wyliczany jako aktualny rozmiar listy rekordów w topicu.
     *
     * Przykład:
     * - pierwszy rekord w topicu ma offset 0,
     * - drugi ma offset 1,
     * - trzeci ma offset 2.
     *
     * key jest zapisywany w rekordzie, ale ta implementacja nie używa go do partycjonowania,
     * ponieważ nie symuluje partycji Kafki.
     */
    @Override
    public void publish(String topic, String key, IntegrationEventEnvelope envelope) {
        var topicRecords = records.computeIfAbsent(
                topic,
                ignored -> new CopyOnWriteArrayList<>()
        );

        topicRecords.add(
                new KafkaRecord(
                        topic,
                        0,
                        key,
                        envelope,
                        topicRecords.size()
                )
        );
    }

    /**
     * Pobiera rekordy z topicu dla danej consumer group.
     *
     * Zwracamy tylko rekordy z offsetem większym niż ostatni zatwierdzony offset.
     *
     * Jeśli committedOffset = -1, oznacza to, że grupa nie przetworzyła jeszcze
     * żadnego rekordu, więc poll zwróci rekordy od offsetu 0.
     *
     * maxRecords ogranicza liczbę rekordów zwracanych w jednym odczycie.
     */
    @Override
    public List<KafkaRecord> poll(String topic, String consumerGroup, int maxRecords) {
        long committed = committedOffset(topic, consumerGroup);

        return records.getOrDefault(topic, List.of()).stream()
                .filter(record -> record.offset() > committed)
                .limit(maxRecords)
                .toList();
    }

    /**
     * Zatwierdza offset dla topicu i consumer group.
     *
     * Używamy Math::max, żeby przypadkowe commitowanie starszego offsetu nie cofnęło
     * postępu konsumenta.
     */
    @Override
    public void commit(String topic, String consumerGroup, long offset) {
        committedOffsets.merge(
                key(topic, consumerGroup),
                offset,
                Math::max
        );
    }

    /**
     * Zwraca ostatni zatwierdzony offset dla topicu i consumer group.
     *
     * Jeśli grupa nie commitowała jeszcze żadnego offsetu, zwracamy -1.
     */
    @Override
    public long committedOffset(String topic, String consumerGroup) {
        return committedOffsets.getOrDefault(
                key(topic, consumerGroup),
                -1L
        );
    }

    /**
     * Zwraca ostatni offset w topicu.
     *
     * Jeśli topic nie ma rekordów, zwracamy -1.
     *
     * Uwaga: w prawdziwej Kafce pojęcie end offset bywa rozumiane jako offset następnego
     * rekordu do zapisania. Tutaj przyjmujemy prostszy model: ostatni istniejący offset.
     */
    @Override
    public long endOffset(String topic) {
        return records.getOrDefault(topic, List.of()).size() - 1L;
    }

    /**
     * Oblicza lag konsumenta.
     *
     * Lag = ostatni offset w topicu - ostatni commitowany offset.
     *
     * Jeśli wynik byłby ujemny, zwracamy 0.
     */
    public long lag(String topic, String consumerGroup) {
        long end = endOffset(topic);
        long committed = committedOffset(topic, consumerGroup);

        return Math.max(0, end - committed);
    }

    /**
     * Zwraca kopię rekordów z danego topicu.
     *
     * Metoda jest przydatna w testach, gdy chcemy sprawdzić, jakie wiadomości
     * zostały opublikowane przez KafkaOutboxWorker.
     */
    public List<KafkaRecord> records(String topic) {
        return List.copyOf(
                records.getOrDefault(topic, List.of())
        );
    }

    /**
     * Czyści wszystkie rekordy i commity.
     *
     * Przydatne w testach między scenariuszami.
     */
    public void clear() {
        records.clear();
        committedOffsets.clear();
    }

    /**
     * Buduje techniczny klucz dla mapy committedOffsets.
     */
    private String key(String topic, String consumerGroup) {
        return topic + "::" + consumerGroup;
    }
}