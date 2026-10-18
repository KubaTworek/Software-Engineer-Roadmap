package com.ridesharing.mvp.matching;

import com.ridesharing.mvp.ride.RideService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Granica pomiędzy transakcją agregatu Ride a asynchronicznym matchingiem.
 * Sam algorytm wybiera i rezerwuje kandydata, natomiast RideService nadal jest
 * jedynym właścicielem zmiany stanu przejazdu.
 */
@Component
@RequiredArgsConstructor
public class MatchingCoordinator {
    private final MatchingService matchingService;
    private final RideService rideService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void match(MatchingRequestedEvent event) {
        matchingService.reserveCandidate(event.rideId(), event.cityId(), event.pickupLat(), event.pickupLng())
                .ifPresentOrElse(
                        driverId -> rideService.offerDriver(event.rideId(), driverId),
                        () -> rideService.markFailed(event.rideId(), "No available drivers nearby")
                );
    }
}
