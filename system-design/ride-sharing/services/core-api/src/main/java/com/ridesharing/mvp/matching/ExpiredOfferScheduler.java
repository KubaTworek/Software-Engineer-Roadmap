package com.ridesharing.mvp.matching;

import com.ridesharing.mvp.driver.DriverAvailabilityStatus;
import com.ridesharing.mvp.driver.DriverRepository;
import com.ridesharing.mvp.ride.RideService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Odzyskuje kierowców po ofercie, na którą aplikacja mobilna nie odpowiedziała.
 * Właściwa metoda domenowa ponownie sprawdza ride, driver i termin pod blokadą,
 * dlatego wynik tego zapytania jest tylko listą kandydatów, nie decyzją.
 */
@Component
@RequiredArgsConstructor
public class ExpiredOfferScheduler {
    private final DriverRepository drivers;
    private final RideService rides;

    @Scheduled(fixedDelayString = "${app.matching.expired-offer-scan-ms:5000}")
    public void recoverExpiredOffers() {
        drivers.findTop100ByAvailabilityStatusAndOfferExpiresAtBefore(
                        DriverAvailabilityStatus.OFFERED_RIDE,
                        Instant.now()
                ).forEach(driver -> {
                    if (driver.getActiveRideId() != null) {
                        rides.expireOffer(driver.getActiveRideId(), driver.getId());
                    }
                });
    }
}
