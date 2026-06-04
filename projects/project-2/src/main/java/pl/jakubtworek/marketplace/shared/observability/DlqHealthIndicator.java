package pl.jakubtworek.marketplace.shared.observability;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import pl.jakubtworek.marketplace.integration.kafka.DlqEventRepository;
import pl.jakubtworek.marketplace.integration.kafka.DlqEventStatus;

/**
 * Health indicator sprawdzający stan DLQ.
 *
 * Ten komponent integruje się ze Spring Boot Actuator.
 * Dzięki temu informacja o eventach znajdujących się w DLQ może być widoczna
 * w endpointach healthchecków, np.:
 *
 * GET /actuator/health
 *
 * DLQ, czyli Dead Letter Queue, przechowuje eventy, których konsument nie był
 * w stanie poprawnie przetworzyć po wykorzystaniu dostępnych prób retry.
 *
 * Jeśli w DLQ znajdują się nowe eventy, system technicznie może nadal działać,
 * ale wymaga uwagi operatora. Dlatego zwracamy status DEGRADED, a nie DOWN.
 */
@Component
public class DlqHealthIndicator implements HealthIndicator {

    /**
     * Repozytorium eventów DLQ.
     *
     * Używane do sprawdzenia, ile eventów ma status NEW, czyli czeka na analizę
     * albo ręczne ponowienie.
     */
    private final DlqEventRepository repository;

    public DlqHealthIndicator(DlqEventRepository repository) {
        this.repository = repository;
    }

    /**
     * Zwraca stan zdrowia komponentu DLQ.
     *
     * Logika:
     * - jeśli istnieją nowe eventy w DLQ, zwracamy status DEGRADED,
     * - jeśli nie ma nowych eventów w DLQ, zwracamy UP.
     *
     * Status DEGRADED oznacza, że aplikacja nie jest całkowicie niedostępna,
     * ale istnieje problem operacyjny wymagający sprawdzenia.
     *
     * Detail newDlqEvents pokazuje liczbę nowych eventów w DLQ.
     */
    @Override
    public Health health() {
        int dlq = repository.findByStatus(DlqEventStatus.NEW, 1000).size();

        if (dlq > 0) {
            return Health.status("DEGRADED")
                    .withDetail("newDlqEvents", dlq)
                    .build();
        }

        return Health.up()
                .withDetail("newDlqEvents", 0)
                .build();
    }
}