package pl.jakubtworek.marketplace.shared.observability;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import pl.jakubtworek.marketplace.integration.outbox.OutboxEventRepository;
import pl.jakubtworek.marketplace.integration.outbox.OutboxEventStatus;

@Component
public class OutboxHealthIndicator implements HealthIndicator {
    private final OutboxEventRepository repository;

    public OutboxHealthIndicator(OutboxEventRepository repository) {
        this.repository = repository;
    }

    @Override
    public Health health() {
        int failed = repository.findByStatus(OutboxEventStatus.FAILED, 1000).size();
        if (failed > 0) {
            return Health.status("DEGRADED").withDetail("failedOutboxEvents", failed).build();
        }
        return Health.up().withDetail("failedOutboxEvents", 0).build();
    }
}
