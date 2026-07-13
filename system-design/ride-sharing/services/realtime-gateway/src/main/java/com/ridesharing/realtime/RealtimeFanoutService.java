package com.ridesharing.realtime;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

/**
 * Serwis fanoutu zdarzeń realtime do klientów WebSocket/STOMP.
 *
 * W aplikacji ride-sharing ten komponent odpowiada za wypychanie aktualizacji
 * do pasażerów, kierowców albo paneli operacyjnych bez konieczności ciągłego pollingu.
 *
 * Typowe użycia:
 * - zmiana statusu przejazdu,
 * - przypisanie kierowcy,
 * - aktualizacja pozycji kierowcy,
 * - podgląd lokalizacji w mieście,
 * - dashboard realtime.
 *
 * Ważne: WebSocket jest kanałem powiadomień, nie źródłem prawdy.
 * Po reconnect albo utracie wiadomości klient powinien odświeżyć stan przez REST.
 */
@Service
public class RealtimeFanoutService {

    /**
     * Springowy mechanizm wysyłania wiadomości do brokerów STOMP.
     *
     * convertAndSend publikuje wiadomość na destination, np.:
     * - /topic/rides/{rideId}
     * - /topic/cities/{cityId}/locations
     *
     * Klienci subskrybują te destination przez WebSocket.
     */
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Konstruktor wstrzykujący SimpMessagingTemplate.
     */
    public RealtimeFanoutService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Publikuje event dotyczący konkretnego przejazdu.
     *
     * Destination:
     * /topic/rides/{rideId}
     *
     * Taki event może zawierać np.:
     * - RideRequested,
     * - DriverAssigned,
     * - DriverArrived,
     * - RideStarted,
     * - RideCompleted,
     * - RideCancelled.
     *
     * Payload jest opakowany w RealtimeEvent, żeby frontend dostał spójny format:
     * - typ eventu,
     * - aggregateId,
     * - cityId,
     * - dane domenowe,
     * - timestamp.
     */
    public void publishRideEvent(
            String rideId,
            Map<String, Object> payload
    ) {
        messagingTemplate.convertAndSend(
                "/topic/rides/" + rideId,
                new RealtimeEvent(
                        "RIDE_EVENT",
                        rideId,
                        city(payload),
                        payload,
                        Instant.now()
                )
        );
    }

    /**
     * Publikuje event lokalizacji kierowcy dla danego miasta.
     *
     * Destination:
     * /topic/cities/{cityId}/locations
     *
     * To może zasilać:
     * - panel operacyjny miasta,
     * - monitoring podaży kierowców,
     * - mapę heatmap/live vehicles,
     * - narzędzia supportu.
     *
     * aggregateId ustawiany jest na driverId z payloadu.
     */
    public void publishCityLocationEvent(
            String cityId,
            Map<String, Object> payload
    ) {
        messagingTemplate.convertAndSend(
                "/topic/cities/" + cityId + "/locations",
                new RealtimeEvent(
                        "LOCATION_EVENT",
                        String.valueOf(payload.get("driverId")),
                        cityId,
                        payload,
                        Instant.now()
                )
        );
    }

    /**
     * Wyciąga cityId z payloadu.
     *
     * Jeśli payload nie zawiera cityId, zwracamy "unknown".
     * To pozwala nadal wysłać event, ale utrudnia routing i monitoring.
     *
     * Produkcyjnie brak cityId w eventach realtime powinien być metryką albo błędem walidacji,
     * bo cityId jest ważne dla shardingu, routingów i uprawnień.
     */
    private String city(Map<String, Object> payload) {
        Object value = payload.get("cityId");
        return value == null
                ? "unknown"
                : value.toString();
    }
}