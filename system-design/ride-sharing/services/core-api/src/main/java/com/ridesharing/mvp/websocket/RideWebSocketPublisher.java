package com.ridesharing.mvp.websocket;

import com.ridesharing.mvp.ride.Ride;
import com.ridesharing.mvp.ride.RideEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Komponent publikujący aktualizacje przejazdu przez WebSocket/STOMP.
 *
 * W aplikacji ride-sharing odpowiada za real-time komunikację z klientami:
 * - pasażer widzi zmianę statusu przejazdu,
 * - pasażer widzi przypisanego kierowcę,
 * - kierowca widzi aktualizacje dotyczące swojego kursu,
 * - frontend nie musi stale odpytywać REST API.
 *
 * To jest warstwa transportowa. Nie powinna decydować o statusach przejazdu
 * ani wykonywać logiki domenowej.
 */
@Component
@RequiredArgsConstructor
public class RideWebSocketPublisher {

    /**
     * Springowy mechanizm wysyłania wiadomości STOMP/WebSocket.
     *
     * SimpMessagingTemplate pozwala publikować wiadomości:
     * - na publiczne tematy, np. /topic/rides/{rideId},
     * - do konkretnego użytkownika, np. /user/{email}/queue/rides.
     */
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Publikuje event przejazdu do zainteresowanych klientów.
     *
     * Flow:
     * 1. Buduje RideEvent na podstawie aktualnego stanu Ride.
     * 2. Wysyła event na topic konkretnego przejazdu.
     * 3. Wysyła event bezpośrednio do pasażera.
     * 4. Jeżeli kierowca jest przypisany, wysyła event także do kierowcy.
     *
     * Typowe wywołania tej metody następują po zmianach statusu:
     * - RideRequested,
     * - DriverAssigned,
     * - DriverAcceptedRide,
     * - DriverArrived,
     * - RideStarted,
     * - RideCompleted,
     * - RideCancelled.
     */
    public void publishRideEvent(Ride ride, String message) {
        /*
         * RideEvent powinien być lekkim DTO dla frontendu.
         * Nie wysyłamy całej encji JPA, tylko kontrolowany snapshot statusu przejazdu.
         */
        var event = RideEvent.of(ride, message);

        /*
         * Publiczny topic dla konkretnego przejazdu.
         *
         * Klient może subskrybować:
         * /topic/rides/{rideId}
         *
         * Uwaga: jeżeli ten topic nie jest odpowiednio zabezpieczony,
         * każdy połączony klient znający rideId mógłby próbować nasłuchiwać cudzego przejazdu.
         */
        messagingTemplate.convertAndSend(
                "/topic/rides/" + ride.getId(),
                event
        );

        /*
         * Prywatna kolejka pasażera.
         *
         * Spring mapuje to do destination typu:
         * /user/queue/rides
         *
         * Wysyłka po emailu zakłada, że Principal.name w WebSocket Security
         * jest równy emailowi użytkownika.
         */
        messagingTemplate.convertAndSendToUser(
                ride.getPassenger().getEmail(),
                "/queue/rides",
                event
        );

        /*
         * Prywatna kolejka kierowcy.
         *
         * Event wysyłamy tylko wtedy, gdy ride ma już przypisanego kierowcę.
         * Na wcześniejszych etapach, np. REQUESTED/MATCHING, driver może być null.
         */
        if (ride.getDriver() != null) {
            messagingTemplate.convertAndSendToUser(
                    ride.getDriver().getUser().getEmail(),
                    "/queue/rides",
                    event
            );
        }
    }
}