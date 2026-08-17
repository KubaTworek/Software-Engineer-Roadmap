package com.ridesharing.mvp.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Serwis odpowiedzialny za zapisywanie eventów domenowych do tabeli outbox.
 *
 * W aplikacji ride-sharing OutboxService jest używany przez serwisy domenowe,
 * np. RideService, PaymentService albo SupportService, gdy zmiana w bazie
 * powinna później zostać opublikowana do Kafki.
 *
 * Ta klasa nie publikuje eventów bezpośrednio do Kafki.
 * Jej zadaniem jest trwały zapis eventu w tej samej transakcji,
 * w której zmieniany jest stan domeny.
 */
@Service
@RequiredArgsConstructor
public class OutboxService {

    /**
     * Repozytorium eventów outbox.
     *
     * Event zapisany tutaj dostaje status PENDING.
     * Później OutboxPublisher pobierze go i wyśle do Kafki.
     */
    private final OutboxEventRepository outbox;

    /**
     * ObjectMapper serializuje payload eventu do JSON-a.
     *
     * W tabeli outbox przechowujemy gotowy JSON, żeby publisher nie musiał znać
     * typów domenowych ani budować payloadu od nowa.
     */
    private final ObjectMapper objectMapper;

    /**
     * Topic Kafki dla eventów związanych z przejazdami.
     *
     * Przykładowe eventy:
     * - RideRequested,
     * - DriverOffered,
     * - DriverAssigned,
     * - RideStarted,
     * - RideCompleted,
     * - RideCancelled.
     */
    @Value("${app.kafka.topics.ride-events:ride.events}")
    private String rideEventsTopic;

    /**
     * Topic Kafki dla eventów płatniczych.
     *
     * Przykładowe eventy:
     * - PaymentAuthorized,
     * - PaymentCaptured,
     * - PaymentFailed,
     * - RefundIssued.
     */
    @Value("${app.kafka.topics.payment-events:payment.events}")
    private String paymentEventsTopic;

    /**
     * Topic Kafki dla eventów supportowych.
     *
     * Przykładowe eventy:
     * - SupportTicketCreated,
     * - SupportTicketAssigned,
     * - SupportTicketResolved.
     */
    @Value("${app.kafka.topics.support-events:support.events}")
    private String supportEventsTopic;

    /**
     * Zapisuje event dotyczący przejazdu.
     *
     * rideId jest aggregateId, czyli identyfikatorem agregatu domenowego.
     * Dzięki temu Kafka może użyć rideId jako key i zachować kolejność eventów
     * dla tego samego przejazdu.
     *
     * Ten event trafi później do topicu ride.events.
     */
    @Transactional
    public void rideEvent(UUID rideId, String eventType, Map<String, Object> payload) {
        save("Ride", rideId, eventType, rideEventsTopic, payload);
    }

    /**
     * Zapisuje event dotyczący płatności.
     *
     * paymentId jest aggregateId dla płatności.
     * Eventy płatnicze są oddzielone od eventów przejazdu, bo mogą mieć osobnych consumerów,
     * np. reconciliation, faktury, portfel kierowcy albo antifraud.
     */
    @Transactional
    public void paymentEvent(UUID paymentId, String eventType, Map<String, Object> payload) {
        save("Payment", paymentId, eventType, paymentEventsTopic, payload);
    }

    /**
     * Zapisuje event dotyczący ticketu supportowego.
     *
     * Support eventy mogą być konsumowane przez:
     * - system powiadomień,
     * - panel admina,
     * - analitykę jakości obsługi,
     * - automatyzacje supportowe.
     */
    @Transactional
    public void supportEvent(UUID ticketId, String eventType, Map<String, Object> payload) {
        save("SupportTicket", ticketId, eventType, supportEventsTopic, payload);
    }

    /**
     * Wspólna metoda tworząca rekord outbox.
     *
     * Parametry:
     * - aggregateType: typ domeny, np. Ride, Payment, SupportTicket,
     * - aggregateId: ID konkretnego agregatu,
     * - eventType: nazwa zdarzenia domenowego,
     * - topic: topic Kafki, do którego event ma zostać opublikowany,
     * - payload: dane eventu.
     *
     * Status początkowy zawsze ustawiany jest na PENDING.
     * To oznacza, że event został zapisany w bazie, ale nie został jeszcze wysłany do Kafki.
     */
    private void save(
            String aggregateType,
            UUID aggregateId,
            String eventType,
            String topic,
            Map<String, Object> payload
    ) {
        try {
            outbox.save(OutboxEvent.builder()
                    .id(UUID.randomUUID())
                    .aggregateType(aggregateType)
                    .aggregateId(aggregateId)
                    .eventType(eventType)
                    .topic(topic)

                    /*
                     * Payload zapisujemy jako JSON.
                     * Publisher wysyła później dokładnie ten JSON do Kafki.
                     */
                    .payload(objectMapper.writeValueAsString(payload))

                    /*
                     * PENDING oznacza, że event czeka na publikację przez OutboxPublisher.
                     */
                    .status(OutboxStatus.PENDING)
                    .build());
        } catch (Exception ex) {
            /*
             * Jeżeli nie umiemy zserializować payloadu, nie wolno udawać sukcesu.
             * Event nie trafiłby do outboxa, więc inne systemy nie dowiedziałyby się o zmianie.
             */
            throw new IllegalStateException("Cannot serialize outbox payload", ex);
        }
    }
}