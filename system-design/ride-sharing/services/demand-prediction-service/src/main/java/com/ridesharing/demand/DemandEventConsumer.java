package com.ridesharing.demand;

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

    /**
     * Konstruktor wstrzykujący DemandPredictionService.
     */
    public DemandEventConsumer(DemandPredictionService service) {
        this.service = service;
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
     * To jest prosty parser MVP, oparty na wyszukiwaniu tekstu.
     * Produkcyjnie powinien zostać zastąpiony deserializacją JSON-a do DTO/event envelope.
     */
    @KafkaListener(
            topics = "ride.events",
            groupId = "demand-prediction"
    )
    public void consume(String payload) {
        /*
         * Miasto, którego dotyczy event.
         * Jeśli event nie zawiera cityId, używamy "default".
         */
        String city = extract(payload, "cityId", "default");

        /*
         * Komórka H3, której dotyczy event.
         * Jeśli event nie zawiera h3Cell, fallback to "city".
         *
         * Dzięki temu request nadal zasili prognozę miejską,
         * nawet jeśli brakuje dokładnego indeksu przestrzennego.
         */
        String cell = extract(payload, "h3Cell", "city");

        /*
         * Rejestrujemy popyt tylko dla eventu RideRequested.
         *
         * Inne eventy z ride.events, np. RideCompleted albo RideCancelled,
         * nie powinny zwiększać popytu.
         */
        if (payload.contains("RideRequested")) {
            service.recordRideRequested(city, cell);
        }
    }

    /**
     * Bardzo prosty extractor wartości z JSON-a zapisanego jako String.
     *
     * Szuka wzorca:
     * "key":"value"
     *
     * Jeśli nie znajdzie pola albo format jest inny, zwraca fallback.
     *
     * To rozwiązanie jest kruche:
     * - nie obsłuży spacji po dwukropku,
     * - nie obsłuży zagnieżdżonych struktur,
     * - nie obsłuży escaped quotes,
     * - nie rozróżnia typów JSON,
     * - może błędnie działać przy zmianie formatu eventu.
     *
     * W produkcji należy użyć ObjectMappera i jawnego DTO.
     */
    private String extract(String payload, String key, String fallback) {
        String marker = "\"" + key + "\":\"";

        int i = payload.indexOf(marker);
        if (i < 0) {
            return fallback;
        }

        int start = i + marker.length();
        int end = payload.indexOf("\"", start);

        return end > start
                ? payload.substring(start, end)
                : fallback;
    }
}