package com.ridesharing.realtime;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RealtimeFanoutServiceTest {
    @Test
    void rideEventUsesPrivateParticipantQueuesAndNeverPublicRideTopic() {
        var messaging = mock(SimpMessagingTemplate.class);
        var fanout = new RealtimeFanoutService(messaging);

        fanout.publishRideEvent("ride-1", Map.of(
                "passengerUsername", "passenger@example.test",
                "driverUsername", "driver@example.test",
                "status", "DRIVER_ASSIGNED"
        ));

        verify(messaging).convertAndSendToUser(eq("passenger@example.test"), eq("/queue/rides"), any());
        verify(messaging).convertAndSendToUser(eq("driver@example.test"), eq("/queue/rides"), any());
        verify(messaging, never()).convertAndSend(startsWith("/topic/rides/"), any(Object.class));
    }
}
