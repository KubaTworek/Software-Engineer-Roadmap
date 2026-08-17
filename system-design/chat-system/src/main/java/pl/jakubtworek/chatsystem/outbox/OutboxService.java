package pl.jakubtworek.chatsystem.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Serwis odpowiedzialny za dopisywanie zdarzeń domenowych do outboxa.
 *
 * Outbox pattern rozwiązuje ważny problem systemów event-driven:
 *
 * Co jeśli aplikacja zapisze wiadomość do bazy,
 * ale nie zdąży opublikować eventu "message.created" do kolejki?
 *
 * Bez outboxa moglibyśmy mieć niespójność:
 * - wiadomość istnieje w bazie,
 * - ale WebSocket, powiadomienia push, search indexer albo analytics
 *   nigdy się o niej nie dowiedzą.
 *
 * Dlatego event zapisujemy do tabeli outbox_events w tej samej transakcji
 * co zmianę biznesową, np. zapis wiadomości.
 *
 * Później osobny worker odczytuje nieopublikowane eventy z outboxa
 * i publikuje je dalej do lokalnej kolejki, Kafka, RabbitMQ, NATS itd.
 */
@Service
public class OutboxService {

    /**
     * Repozytorium tabeli outbox_events.
     *
     * Odpowiada za trwały zapis eventów w bazie danych.
     */
    private final OutboxEventRepository repository;

    /**
     * ObjectMapper zamienia payload eventu na JSON.
     *
     * Dzięki temu outbox może przechowywać różne typy eventów
     * w jednej tabeli jako tekst JSON.
     */
    private final ObjectMapper objectMapper;

    public OutboxService(OutboxEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * Dopisuje nowe zdarzenie do outboxa.
     *
     * Parametry:
     * - aggregateId — identyfikator obiektu domenowego, którego dotyczy event,
     *   np. messageId dla eventu MESSAGE_CREATED,
     * - eventType — typ zdarzenia, np. "message.created",
     * - payload — dane eventu, które zostaną zapisane jako JSON.
     *
     * Ta metoda powinna być wywoływana wewnątrz tej samej transakcji,
     * w której zapisujemy właściwe dane biznesowe.
     *
     * Przykład:
     * - zapisujemy Message,
     * - aktualizujemy Conversation.lastMessage,
     * - dopisujemy MESSAGE_CREATED do outboxa,
     * - całość commitujemy razem.
     *
     * Dzięki temu albo wszystko się zapisze, albo nic.
     */
    public OutboxEvent append(UUID aggregateId, String eventType, Object payload) {
        try {
            /*
             * Serializujemy payload do JSON.
             *
             * OutboxEvent nie musi znać konkretnej klasy eventu.
             * Przechowuje typ eventu oraz JSON z danymi.
             */
            String json = objectMapper.writeValueAsString(payload);

            /*
             * Zapisujemy event jako rekord w outbox_events.
             *
             * Na tym etapie event nie musi być jeszcze opublikowany.
             * Publikacją zajmie się osobny worker.
             */
            return repository.save(new OutboxEvent(aggregateId, eventType, json));

        } catch (JsonProcessingException ex) {
            /*
             * Jeśli payloadu nie da się zserializować,
             * traktujemy to jako błąd programistyczny/konfiguracyjny.
             *
             * Nie zapisujemy wtedy niekompletnego eventu,
             * bo worker nie byłby w stanie go później poprawnie obsłużyć.
             */
            throw new IllegalStateException("Cannot serialize outbox event payload", ex);
        }
    }
}