package com.ridesharing.fraud;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Consumer Kafki dla Fraud Service.
 *
 * W aplikacji ride-sharing Fraud Service powinien obserwować sygnały z różnych części systemu:
 * - przejazdy,
 * - płatności,
 * - lokalizacje kierowców.
 *
 * Ten consumer jest wejściem strumieniowym dla fraud detection.
 * Na tym etapie tylko loguje odebrane sygnały, ale docelowo powinien aktualizować feature store,
 * budować risk profile użytkowników/kierowców albo uruchamiać reguły wykrywania nadużyć.
 */
@Component
public class FraudKafkaConsumer {

    /**
     * Logger dla sygnałów fraudowych.
     *
     * Logi pozwalają potwierdzić, że Fraud Service faktycznie odbiera eventy z Kafki.
     * W produkcji same logi nie wystarczą — eventy powinny zasilać trwałe feature’y albo modele.
     */
    private static final Logger log = LoggerFactory.getLogger(FraudKafkaConsumer.class);

    /**
     * Konsumuje eventy istotne dla oceny ryzyka.
     *
     * Nasłuchiwane topici:
     * - ride.events: lifecycle przejazdu, anulowania, starty, zakończenia,
     * - payment.events: autoryzacje, capture, błędy płatności,
     * - driver.location.updated: sygnały GPS kierowców.
     *
     * groupId = fraud-service oznacza osobną grupę konsumencką.
     * Fraud Service dostaje własną kopię eventów niezależnie od warehouse,
     * notification service czy innych consumerów.
     *
     * Obecnie metoda tylko loguje:
     * - eventType,
     * - aggregateId.
     *
     * Docelowo tutaj powinny powstawać sygnały typu:
     * - passengerCancellationRate,
     * - driverCancellationRate,
     * - paymentFailureRate,
     * - suspiciousLocationJump,
     * - repeatedPassengerDriverPair,
     * - promoAbuseScore.
     */
    @KafkaListener(
            topics = {
                    "ride.events",
                    "payment.events",
                    "driver.location.updated"
            },
            groupId = "fraud-service"
    )
    public void consume(Map<String, Object> event) {
        /*
         * eventType mówi, jaki sygnał przyszedł.
         * Przykłady:
         * - RideRequested,
         * - RideCancelledByPassenger,
         * - PaymentAuthorizationFailed,
         * - DriverLocationUpdated.
         */
        var eventType = event.get("eventType");

        /*
         * aggregateId identyfikuje obiekt domenowy, którego dotyczy event.
         * Może to być np. rideId, paymentId albo driverId — zależnie od topicu.
         */
        var aggregateId = event.get("aggregateId");

        /*
         * Na tym etapie tylko zapisujemy sygnał do logów.
         * To potwierdza integrację z Kafką, ale nie daje jeszcze realnego fraud detection.
         */
        log.info(
                "fraud_signal_received type={} aggregateId={}",
                eventType,
                aggregateId
        );
    }
}