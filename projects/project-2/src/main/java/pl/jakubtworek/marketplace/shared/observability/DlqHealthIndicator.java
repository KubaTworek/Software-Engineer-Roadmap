package pl.jakubtworek.marketplace.shared.observability;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import pl.jakubtworek.marketplace.integration.kafka.DlqEventRepository;
import pl.jakubtworek.marketplace.integration.kafka.DlqEventStatus;

@Component
public class DlqHealthIndicator implements HealthIndicator {
    private final DlqEventRepository repository;

    public DlqHealthIndicator(DlqEventRepository repository) {
        this.repository = repository;
    }

    @Override
    public Health health() {
        int dlq = repository.findByStatus(DlqEventStatus.NEW, 1000).size();
        if (dlq > 0) {
            return Health.status("DEGRADED").withDetail("newDlqEvents", dlq).build();
        }
        return Health.up().withDetail("newDlqEvents", 0).build();
    }
}
