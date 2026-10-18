package pl.jakubtworek.marketplace.process;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Profile("!postgres")
@Repository
public class InMemoryOrderFulfillmentProcessRepository implements OrderFulfillmentProcessRepository {
    private final ConcurrentHashMap<UUID, OrderFulfillmentProcess> store = new ConcurrentHashMap<>();

    @Override
    public Optional<OrderFulfillmentProcess> findByOrderId(UUID orderId) {
        return Optional.ofNullable(store.get(orderId)).map(this::snapshot);
    }

    @Override
    public OrderFulfillmentProcess save(OrderFulfillmentProcess candidate) {
        store.compute(candidate.orderId(), (id, current) -> {
            long expected = current == null ? 0L : current.version();
            if (candidate.version() != expected) throw new ConcurrentProcessModificationException(id);
            candidate.persistedAs(expected + 1);
            return snapshot(candidate);
        });
        return candidate;
    }

    @Override
    public List<OrderFulfillmentProcess> findExpiredPending(int limit) {
        Instant now = Instant.now();
        return store.values().stream()
                .filter(process -> process.status() == OrderFulfillmentProcess.Status.PENDING)
                .filter(process -> !process.deadline().isAfter(now))
                .limit(limit)
                .map(this::snapshot)
                .toList();
    }

    private OrderFulfillmentProcess snapshot(OrderFulfillmentProcess process) {
        return OrderFulfillmentProcess.restore(process.orderId(), process.deadline(), process.status(),
                process.completedSteps(), process.version());
    }
}
