package pl.jakubtworek.booking.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Trigger nie posiada własnego stanu. Cała decyzja o tym, co wygasło, jest
 * powtarzalna i wykonywana transakcyjnie przez {@link ReservationExpirationService}.
 */
@Component
public class ReservationExpirationScheduler {
    private final ReservationExpirationService expirationService;

    public ReservationExpirationScheduler(ReservationExpirationService expirationService) {
        this.expirationService = expirationService;
    }

    @Scheduled(
            fixedDelayString = "${booking.reservation.expiration-poll-interval:PT30S}",
            initialDelayString = "${booking.reservation.expiration-poll-interval:PT30S}"
    )
    public void expireReservations() {
        expirationService.expireNextBatch();
    }
}
