package com.ridesharing.demand;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumer eventów przejazdów z Kafki dla Demand Prediction Service.
 *
 * W aplikacji ride-sharing ten komponent zbiera sygnały popytu.
 * Gdy w systemie pojawi się event RideRequested, serwis zwiększa licznik requestów
 * dla danego miasta i komórki H3.
 *
 * Te dane są później używane przez DemandPredictionService do prognozowania popytu,
 * driver positioning i dynamic pricingu.
 */
@Component
public class DemandEventConsumer {

    /**
     * Serwis prognozowania popytu.
     *
     * Consumer nie liczy prognozy samodzielnie.
     * Jego rola to tylko przetworzyć event i zarejestrować fakt,
     * że w danym obszarze pojawił się nowy request przejazdu.
     */
    private final DemandPredictionService service;
    private final ObjectMapper objectMapper;

    /**
     * Konstruktor wstrzykujący DemandPredictionService.
     */
    public DemandEventConsumer(DemandPredictionService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    /**
     * Konsumuje eventy z topicu ride.events.
     *
     * groupId = demand-prediction oznacza osobną grupę konsumencką.
     * Dzięki temu Demand Prediction Service dostaje własną kopię eventów
     * niezależnie od innych consumerów, np. warehouse, notification czy fraud.
     *
     * Flow:
     * 1. Odbiera payload eventu jako String.
     * 2. Wyciąga cityId.
     * 3. Wyciąga h3Cell.
     * 4. Jeśli payload dotyczy RideRequested, rejestruje nowy request popytu.
     *
     * Envelope jest parsowany strukturalnie. Nie wyszukujemy tekstu w JSON-ie,
     * ponieważ pole o tej samej nazwie może wystąpić w innym miejscu payloadu.
     */
    @KafkaListener(
            topics = "ride.events",
            groupId = "demand-prediction"
    )
    public void consume(String payload) {
        try {
            JsonNode envelope = objectMapper.readTree(payload);
            if (!"RideRequested".equals(envelope.path("eventType").asText())) {
                return;
            }
            JsonNode eventPayload = envelope.path("payload");
            String city = eventPayload.path("cityId").asText("default");
            String cell = eventPayload.path("h3Cell").asText("city");
            service.recordRideRequested(city, cell);
        } catch (Exception malformedEvent) {
            // Błędnego kontraktu nie zamieniamy w fikcyjny sygnał popytu. Rzutowany
            // wyjątek pozwala kontenerowi Kafka zastosować retry/DLQ ErrorHandlera.
            throw new IllegalArgumentException("Invalid domain event envelope", malformedEvent);
        }
    }
}
