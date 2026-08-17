package com.ridesharing.mlmatching;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumer eventów wejściowych dla feature store ML Matching.
 *
 * W aplikacji ride-sharing model matchingowy potrzebuje danych historycznych
 * i bieżących sygnałów, żeby uczyć się, który kierowca powinien dostać ofertę przejazdu.
 *
 * Najważniejsze źródła sygnałów:
 * - ride.events: requesty, oferty, akceptacje, odrzucenia, anulowania, zakończenia,
 * - driver.location.updated: świeżość lokalizacji, dystans do pickup, ruch kierowcy,
 * - payment.events: problemy z płatnością, fraud/risk proxy, jakość transakcji.
 *
 * Ten komponent jest zalążkiem pipeline'u feature engineering.
 * Obecnie tylko loguje eventy, ale docelowo powinien zasilać online/offline feature store
 * dla modelu rankingu kierowców.
 */
@Component
public class MatchingFeatureConsumer {

    /**
     * Logger dla eventów feature pipeline.
     *
     * Debug log jest przydatny lokalnie do potwierdzenia, że consumer odbiera dane.
     * Produkcyjnie same logi nie są feature storem i nie powinny zawierać wrażliwych danych.
     */
    private static final Logger log = LoggerFactory.getLogger(MatchingFeatureConsumer.class);

    /**
     * Konsumuje eventy potrzebne do budowania cech modelu matchingowego.
     *
     * Topici:
     * - ride.events:
     *   pozwalają policzyć acceptance rate, cancellation rate, czas reakcji kierowcy,
     *   skuteczność matchingu i outcome oferty,
     *
     * - driver.location.updated:
     *   pozwala policzyć ETA, świeżość lokalizacji, kierunek ruchu, dystans do pickup
     *   oraz wykrywać kierowców nieaktywnych albo z niestabilnym GPS,
     *
     * - payment.events:
     *   może zasilać sygnały ryzyka, np. failed payments, chargebacks albo podejrzane wzorce.
     *
     * groupId = ml-matching-feature-store oznacza osobną grupę konsumencką.
     * Dzięki temu ten pipeline dostaje własną kopię eventów niezależnie od Fraud,
     * Warehouse, ML ETA czy innych consumerów.
     *
     * Obecnie payload jest Stringiem i nie jest parsowany.
     * To jest tylko smoke test integracji z Kafką.
     */
    @KafkaListener(
            topics = {
                    "ride.events",
                    "driver.location.updated",
                    "payment.events"
            },
            groupId = "ml-matching-feature-store"
    )
    public void consume(String payload) {
        /*
         * Na tym etapie event jest tylko logowany.
         *
         * Docelowo tutaj powinno się dziać:
         * - parsowanie event envelope,
         * - walidacja schemaVersion,
         * - deduplikacja po eventId,
         * - zapis cech do feature store,
         * - agregacje per driverId / cityId / h3Cell,
         * - budowanie labeli: accepted, rejected, cancelled, completed,
         * - liczenie acceptanceProbability i cancellation risk.
         */
        log.debug("matching-feature-event={}", payload);
    }
}