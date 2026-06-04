package pl.jakubtworek.marketplace.shared.observability;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import pl.jakubtworek.marketplace.integration.outbox.OutboxEventRepository;
import pl.jakubtworek.marketplace.integration.outbox.OutboxEventStatus;

/**
 * Health indicator sprawdzający stan outboxa.
 *
 * Ten komponent integruje się ze Spring Boot Actuator.
 * Dzięki temu informacja o problemach z publikacją eventów z outboxa może być widoczna
 * w endpointach healthchecków, np.:
 *
 * GET /actuator/health
 *
 * Outbox przechowuje eventy, które zostały zapisane razem ze zmianą agregatu,
 * ale niekoniecznie zostały już opublikowane do Kafki albo lokalnego dispatchera.
 *
 * Jeśli eventy mają status FAILED, oznacza to, że worker próbował je opublikować,
 * ale publikacja zakończyła się błędem.
 *
 * Taki stan nie musi oznaczać, że aplikacja jest całkowicie niedostępna,
 * ale wymaga diagnostyki operatora. Dlatego zwracamy status DEGRADED, a nie DOWN.
 */
@Component
public class OutboxHealthIndicator implements HealthIndicator {

    /**
     * Repozytorium outboxa.
     *
     * Używane do sprawdzenia, ile eventów ma status FAILED.
     */
    private final OutboxEventRepository repository;

    public OutboxHealthIndicator(OutboxEventRepository repository) {
        this.repository = repository;
    }

    /**
     * Zwraca stan zdrowia komponentu outbox.
     *
     * Logika:
     * - jeśli istnieją eventy outboxowe ze statusem FAILED, zwracamy DEGRADED,
     * - jeśli nie ma błędnych eventów, zwracamy UP.
     *
     * Szczegół failedOutboxEvents pokazuje liczbę eventów, które wymagają retry
     * albo ręcznej interwencji.
     */
    @Override
    public Health health() {
        int failed = repository.findByStatus(OutboxEventStatus.FAILED, 1000).size();

        if (failed > 0) {
            return Health.status("DEGRADED")
                    .withDetail("failedOutboxEvents", failed)
                    .build();
        }

        return Health.up()
                .withDetail("failedOutboxEvents", 0)
                .build();
    }
}