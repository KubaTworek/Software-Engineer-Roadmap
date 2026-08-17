package com.ridesharing.mleta;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumer eventów wejściowych dla feature store ML ETA.
 *
 * W aplikacji ride-sharing model ETA potrzebuje danych treningowych i cech online/offline.
 * Najważniejsze źródła sygnałów to:
 * - driver.location.updated: bieżące pozycje, prędkość, heading, opóźnienia GPS,
 * - ride.events: lifecycle przejazdu, start, koniec, anulowania, realny czas przejazdu.
 *
 * Ten komponent jest zalążkiem pipeline'u feature engineering.
 * Obecnie tylko loguje eventy, ale docelowo powinien zasilać feature store
 * albo tabelę treningową dla modeli ETA.
 */
@Component
public class FeatureConsumer {

    /**
     * Logger dla eventów feature pipeline.
     *
     * Debug log jest wystarczający do lokalnego potwierdzenia integracji,
     * ale nie zastępuje trwałego zapisu feature'ów.
     */
    private static final Logger log = LoggerFactory.getLogger(FeatureConsumer.class);

    /**
     * Konsumuje eventy potrzebne do budowania cech modelu ETA.
     *
     * Topici:
     * - driver.location.updated: sygnały ruchu i pozycji kierowców,
     * - ride.events: zdarzenia przejazdu, które pozwalają porównać przewidywane ETA z realnym czasem.
     *
     * groupId = ml-eta-feature-store oznacza osobną grupę konsumencką.
     * Dzięki temu ML ETA Service dostaje własną kopię eventów niezależnie od Fraud,
     * Warehouse czy innych consumerów.
     *
     * Obecnie payload jest Stringiem i nie jest parsowany.
     * To dobre tylko jako smoke test integracji z Kafką.
     */
    @KafkaListener(
            topics = {
                    "driver.location.updated",
                    "ride.events"
            },
            groupId = "ml-eta-feature-store"
    )
    public void consume(String payload) {
        /*
         * Na tym etapie event jest tylko logowany.
         *
         * Docelowo tutaj powinno się dziać:
         * - parsowanie event envelope,
         * - walidacja schemaVersion,
         * - zapis cech do feature store,
         * - agregacje per cityId / h3Cell / hourOfDay,
         * - obliczanie realnego czasu przejazdu,
         * - porównanie predicted ETA vs actual ETA.
         */
        log.debug("feature-event={}", payload);
    }
}