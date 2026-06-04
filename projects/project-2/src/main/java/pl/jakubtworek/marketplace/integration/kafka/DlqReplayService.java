package pl.jakubtworek.marketplace.integration.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import pl.jakubtworek.marketplace.integration.outbox.OutboxEventMapper;
import pl.jakubtworek.marketplace.shared.events.ApplicationEventBus;

import java.util.UUID;

/**
 * Serwis odpowiedzialny za ręczny replay eventów z DLQ.
 *
 * DLQ, czyli Dead Letter Queue, przechowuje wiadomości, których konsument nie był
 * w stanie poprawnie przetworzyć po wykorzystaniu dostępnych prób retry.
 *
 * Replay oznacza ponowną próbę przetworzenia takiego eventu, zwykle po usunięciu
 * przyczyny błędu, np.:
 * - poprawieniu błędnego handlera,
 * - dodaniu obsługi nowej wersji eventu,
 * - naprawieniu danych,
 * - poprawieniu konfiguracji.
 *
 * W tej implementacji replay polega na:
 * - pobraniu eventu z repozytorium DLQ,
 * - odtworzeniu DomainEvent z KafkaEnvelope,
 * - przekazaniu eventu do lokalnego ApplicationEventBus,
 * - oznaczeniu eventu DLQ jako REPLAYED albo REPLAY_FAILED.
 *
 * Uwaga:
 * Ten serwis wykonuje replay lokalnie przez ApplicationEventBus.
 * Alternatywnym podejściem jest ponowna publikacja eventu na właściwy topic Kafki.
 * To drugie podejście jest bliższe produkcyjnemu flow, ale lokalny replay jest prostszy
 * i wystarczający do ćwiczenia mechanizmu DLQ w tym projekcie.
 */
@Service
public class DlqReplayService {

    /**
     * Repozytorium eventów DLQ.
     *
     * Służy do pobrania eventu do replay oraz zapisania nowego statusu po próbie replay.
     */
    private final DlqEventRepository dlqRepository;

    /**
     * Lokalny event bus aplikacyjny.
     *
     * Po odtworzeniu DomainEvent serwis przekazuje event do handlerów aplikacyjnych.
     */
    private final ApplicationEventBus eventBus;

    /**
     * Mapper odtwarzający DomainEvent z technicznego OutboxEvent.
     *
     * DLQ przechowuje KafkaEnvelope, więc najpierw zamieniamy envelope na OutboxEvent,
     * a następnie OutboxEvent na właściwy DomainEvent.
     */
    private final OutboxEventMapper outboxEventMapper;

    /**
     * Mapper zamieniający KafkaEnvelope na OutboxEvent.
     *
     * Dzięki temu możemy wykorzystać istniejący OutboxEventMapper do deserializacji
     * konkretnego eventu domenowego.
     */
    private final KafkaEnvelopeMapper envelopeMapper = new KafkaEnvelopeMapper();

    /**
     * Konstruktor używany przez Springa.
     *
     * Wszystkie zależności są wstrzykiwane jako beany.
     * Nie tworzymy ręcznie OutboxEventMapper ani KafkaEnvelopeMapper, żeby uniknąć
     * niespójności konfiguracji i problemów z testowaniem.
     */
    public DlqReplayService(DlqEventRepository dlqRepository, ApplicationEventBus eventBus, ObjectMapper objectMapper) {
        this.dlqRepository = dlqRepository;
        this.eventBus = eventBus;
        this.outboxEventMapper = new OutboxEventMapper(objectMapper);
    }

    /**
     * Wykonuje replay eventu z DLQ.
     *
     * Przepływ:
     * 1. Pobiera event DLQ po ID.
     * 2. Odtwarza DomainEvent z envelope.
     * 3. Publikuje event do lokalnego ApplicationEventBus.
     * 4. Jeśli się uda, oznacza event DLQ jako REPLAYED.
     * 5. Jeśli wystąpi błąd, oznacza event jako REPLAY_FAILED i rzuca wyjątek dalej.
     *
     * Rzucenie wyjątku dalej jest celowe:
     * - endpoint administracyjny dostanie informację, że replay się nie udał,
     * - błąd będzie widoczny w logach,
     * - status eventu DLQ nadal zostanie zaktualizowany.
     */
    public void replay(UUID dlqEventId) {
        var dlqEvent = dlqRepository.findById(dlqEventId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "DLQ event not found: " + dlqEventId
                ));

        try {
            var domainEvent = outboxEventMapper.toDomainEvent(
                    envelopeMapper.toOutboxEvent(dlqEvent.envelope())
            );

            eventBus.publish(domainEvent);

            dlqRepository.save(dlqEvent.markReplayed());
        } catch (Exception e) {
            dlqRepository.save(dlqEvent.markReplayFailed(e.getMessage()));
            throw e;
        }
    }
}