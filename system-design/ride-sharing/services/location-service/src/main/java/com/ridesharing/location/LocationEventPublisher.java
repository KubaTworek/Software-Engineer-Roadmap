package com.ridesharing.location;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publisher eventów lokalizacji kierowcy do Kafki.
 *
 * W osobnym Location Service ta klasa odpowiada za wypychanie zmian lokalizacji
 * do innych części systemu, np.:
 * - Fraud Service,
 * - Data Warehouse,
 * - Demand/Positioning,
 * - monitoring operacyjny,
 * - ewentualne realtime stream processing.
 *
 * Ważne: to nie jest źródło prawdy o lokalizacji.
 * Źródłem prawdy live-location jest LocationService / storage pod spodem,
 * np. Redis, H3 index albo in-memory index w MVP.
 */
@Component
public class LocationEventPublisher {

    /**
     * KafkaTemplate używany do publikacji eventów.
     *
     * Klucz eventu i topic decydują o partycjonowaniu oraz kolejności przetwarzania.
     * Tutaj kluczem jest cityId, więc eventy z tego samego miasta trafią zwykle
     * do tej samej partycji.
     */
    private final KafkaTemplate<String, Object> kafkaTemplate;

    /**
     * Topic dla eventów aktualizacji lokalizacji kierowców.
     *
     * Property:
     * app.kafka.topics.driver-location-updated
     *
     * Przykładowa wartość:
     * driver.location.updated
     */
    private final String locationTopic;

    /**
     * Konstruktor wstrzykujący KafkaTemplate i nazwę topicu z konfiguracji.
     *
     * Dzięki temu topic nie jest zahardkodowany w kodzie i może być różny
     * między lokalnym środowiskiem, stagingiem i produkcją.
     */
    public LocationEventPublisher(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${app.kafka.topics.driver-location-updated}") String locationTopic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.locationTopic = locationTopic;
    }

    /**
     * Publikuje snapshot lokalizacji kierowcy do Kafki.
     *
     * Payloadem jest DriverLocationSnapshot, czyli prawdopodobnie:
     * - driverId,
     * - cityId,
     * - lat/lng,
     * - h3Cell,
     * - heading,
     * - speed,
     * - updatedAt.
     *
     * Kluczem wiadomości jest cityId.
     * To pomaga grupować eventy lokalizacyjne per miasto, co ma sens dla:
     * - analityki miejskiej,
     * - stream processingu per rynek,
     * - podaży/popytu per city.
     */
    public void publish(DriverLocationSnapshot snapshot) {
        kafkaTemplate.send(
                locationTopic,
                snapshot.cityId(),
                snapshot
        );
    }
}