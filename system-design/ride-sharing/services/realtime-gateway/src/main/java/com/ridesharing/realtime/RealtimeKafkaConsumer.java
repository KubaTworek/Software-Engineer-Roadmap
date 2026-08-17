package com.ridesharing.realtime;

import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Consumer Kafki dla Realtime Gateway.
 *
 * W aplikacji ride-sharing ten komponent odbiera eventy domenowe z Kafki
 * i przekazuje je do warstwy WebSocket/STOMP przez RealtimeFanoutService.
 *
 * Dzięki temu core services nie muszą znać konkretnych połączeń WebSocket.
 * Wystarczy, że publikują eventy do Kafki, a realtime-gateway robi fanout
 * do pasażerów, kierowców albo paneli operacyjnych.
 *
 * To jest typowy układ:
 * domain event -> Kafka -> realtime gateway -> WebSocket clients.
 */
@Component
public class RealtimeKafkaConsumer {

    /**
     * Serwis odpowiedzialny za wysyłkę eventów do klientów WebSocket.
     *
     * Consumer nie wysyła wiadomości bezpośrednio przez SimpMessagingTemplate,
     * tylko deleguje to do jednej warstwy fanoutu.
     */
    private final RealtimeFanoutService fanout;

    /**
     * Konstruktor wstrzykujący RealtimeFanoutService.
     */
    public RealtimeKafkaConsumer(RealtimeFanoutService fanout) {
        this.fanout = fanout;
    }

    /**
     * Konsumuje eventy przejazdów z topicu ride.events.
     *
     * groupId = realtime-gateway oznacza, że wszystkie instancje realtime gateway
     * należące do tej samej grupy będą dzieliły między sobą partycje topicu.
     *
     * Flow:
     * 1. Odbiera event jako Map.
     * 2. Próbuje znaleźć rideId.
     * 3. Preferuje aggregateId, a jeśli go nie ma, używa rideId.
     * 4. Jeśli identyfikator istnieje, publikuje event na kanał realtime przejazdu.
     *
     * Destination w fanout:
     * /topic/rides/{rideId}
     */
    @KafkaListener(
            topics = "ride.events",
            groupId = "realtime-gateway"
    )
    public void rideEvent(Map<String, Object> event) {
        /*
         * aggregateId powinien być standardowym identyfikatorem agregatu w event envelope.
         * Fallback do rideId pomaga obsłużyć starszy/prostszy format eventów.
         */
        Object rideId = event.getOrDefault(
                "aggregateId",
                event.get("rideId")
        );

        /*
         * Bez rideId nie da się poprawnie zbudować destination WebSocket,
         * więc event jest pomijany.
         */
        if (rideId != null) {
            fanout.publishRideEvent(
                    rideId.toString(),
                    event
            );
        }
    }

    /**
     * Konsumuje eventy aktualizacji lokalizacji kierowców.
     *
     * Topic:
     * driver.location.updated
     *
     * Flow:
     * 1. Odbiera event lokalizacji jako Map.
     * 2. Wyciąga cityId.
     * 3. Jeśli cityId istnieje, publikuje event na kanał lokalizacji miasta.
     *
     * Destination w fanout:
     * /topic/cities/{cityId}/locations
     *
     * Ten strumień powinien być traktowany ostrożnie, bo może zawierać
     * bardzo wrażliwe dane GPS i generować ogromny wolumen wiadomości.
     */
    @KafkaListener(
            topics = "driver.location.updated",
            groupId = "realtime-gateway"
    )
    public void locationEvent(Map<String, Object> event) {
        /*
         * cityId jest wymagane do routingu po mieście.
         * Bez niego nie wiemy, na który kanał realtime wysłać lokalizację.
         */
        Object cityId = event.get("cityId");

        if (cityId != null) {
            fanout.publishCityLocationEvent(
                    cityId.toString(),
                    event
            );
        }
    }
}