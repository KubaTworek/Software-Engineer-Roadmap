package pl.jakubtworek.backend.reservation.application;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pl.jakubtworek.backend.reservation.coordination.PostgresLeaseCoordinator;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;

/** Dwie repliki mogą tykać jednocześnie; tylko właściciel aktualnego tokenu zapisze wynik. */
@Component
public class ReservationExpiryWorker {
    private final String owner = UUID.randomUUID().toString();
    private final PostgresLeaseCoordinator leases;
    private final ReservationService service;
    private final Clock clock;

    public ReservationExpiryWorker(PostgresLeaseCoordinator leases, ReservationService service, Clock clock) {
        this.leases = leases; this.service = service; this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.expiry.poll-delay:5s}")
    public void run() {
        leases.tryAcquire("reservation-expiry", owner, clock.instant(), Duration.ofSeconds(15))
                .ifPresent(token -> service.expireBatch(token, clock.instant()));
    }
}
