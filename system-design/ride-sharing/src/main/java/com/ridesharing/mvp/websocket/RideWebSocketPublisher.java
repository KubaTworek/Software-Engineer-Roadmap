package com.ridesharing.mvp.websocket;

import com.ridesharing.mvp.ride.Ride;
import com.ridesharing.mvp.ride.RideEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RideWebSocketPublisher {
    private final SimpMessagingTemplate messagingTemplate;

    public void publishRideEvent(Ride ride, String message) {
        var event = RideEvent.of(ride, message);
        messagingTemplate.convertAndSend("/topic/rides/" + ride.getId(), event);
        messagingTemplate.convertAndSendToUser(ride.getPassenger().getEmail(), "/queue/rides", event);
        if (ride.getDriver() != null) {
            messagingTemplate.convertAndSendToUser(ride.getDriver().getUser().getEmail(), "/queue/rides", event);
        }
    }
}
