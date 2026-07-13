package com.ridesharing.warehouse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Consumer Kafki zapisujący eventy domenowe do Data Warehouse.
 *
 * W architekturze ride-sharing ten komponent zbiera zdarzenia z różnych części systemu:
 * - przejazdy,
 * - płatności,
 * - support,
 * - lokalizacje kierowców.
 *
 * Jego zadaniem nie jest wykonywanie logiki biznesowej.
 * To pipeline analityczny: konsumuje event, normalizuje kilka podstawowych pól
 * i zapisuje pełny payload do tabeli fact_events.
 */
@Component
public class WarehouseConsumer {

    /**
     * JdbcTemplate do zapisu eventów w bazie warehouse.
     *
     * Użycie JDBC jest tu sensowne, bo zapisujemy rekord faktu/analityczny,
     * a nie pracujemy na bogatym modelu domenowym JPA.
     */
    private final JdbcTemplate jdbc;

    /**
     * ObjectMapper serializuje odebrany event do JSON-a.
     *
     * Pełny payload jest zapisywany w kolumnie JSONB, żeby nie tracić danych,
     * nawet jeśli warehouse normalizuje tylko część pól.
     */
    private final ObjectMapper objectMapper;

    /**
     * Konstruktor wstrzykujący zależności.
     *
     * Consumer nie tworzy połączenia do bazy ani ObjectMappera samodzielnie.
     * Korzysta z konfiguracji Spring Boot.
     */
    public WarehouseConsumer(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    /**
     * Konsumuje eventy z topiców Kafki i zapisuje je do fact_events.
     *
     * Nasłuchiwane topici:
     * - ride.events,
     * - payment.events,
     * - support.events,
     * - driver.location.updated.
     *
     * groupId = data-warehouse-consumer oznacza osobną grupę konsumencką warehouse.
     * Dzięki temu ten consumer dostaje własną kopię eventów niezależnie od innych consumerów,
     * np. notyfikacji, fraudu czy realtime gateway.
     *
     * Flow:
     * 1. Kafka dostarcza event jako Map<String, Object>.
     * 2. Consumer pobiera topic z nagłówka KafkaHeaders.RECEIVED_TOPIC.
     * 3. Wyciąga podstawowe pola: eventType, aggregateId, cityId.
     * 4. Serializuje cały event do JSON-a.
     * 5. Zapisuje rekord do tabeli fact_events.
     */
    @KafkaListener(
            topics = {
                    "ride.events",
                    "payment.events",
                    "support.events",
                    "driver.location.updated"
            },
            groupId = "data-warehouse-consumer"
    )
    public void consume(
            Map<String, Object> event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic
    ) throws JsonProcessingException {
        /*
         * Typ eventu, np. RideRequested, PaymentCaptured, SupportTicketCreated.
         * Jeśli producer nie doda eventType, wartość będzie null.
         */
        String eventType = string(event.get("eventType"));

        /*
         * aggregateId identyfikuje obiekt domenowy, którego dotyczy event.
         *
         * Fallback na rideId obsługuje starsze/prostsze eventy ride,
         * które nie mają jeszcze zunifikowanej koperty eventu.
         */
        String aggregateId = string(
                event.getOrDefault("aggregateId", event.get("rideId"))
        );

        /*
         * cityId pozwala później analizować wolumen eventów per miasto.
         * Nie każdy event musi mieć cityId, dlatego dopuszczamy null.
         */
        String cityId = string(event.get("cityId"));

        /*
         * Pełny event zapisujemy jako JSON.
         * Dzięki temu warehouse może później odczytać dodatkowe pola bez zmiany consumera.
         */
        String payload = objectMapper.writeValueAsString(event);

        /*
         * Zapis do tabeli faktów.
         *
         * source_topic mówi, z którego strumienia pochodzi event.
         * event_type, aggregate_id i city_id są kolumnami ułatwiającymi filtrowanie/agregację.
         * payload przechowuje pełny JSON eventu.
         */
        jdbc.update(
                """
                INSERT INTO fact_events(
                    source_topic,
                    event_type,
                    aggregate_id,
                    city_id,
                    payload
                )
                VALUES (?, ?, ?, ?, ?::jsonb)
                """,
                topic,
                eventType,
                aggregateId,
                cityId,
                payload
        );
    }

    /**
     * Bezpiecznie zamienia wartość z eventu na String.
     *
     * Kafka event jest mapą, więc wartości mogą mieć różne typy:
     * String, UUID jako String, Integer, Long, null.
     *
     * Ta metoda centralizuje prostą konwersję i zachowuje null jako null.
     */
    private String string(Object value) {
        return value == null ? null : value.toString();
    }
}