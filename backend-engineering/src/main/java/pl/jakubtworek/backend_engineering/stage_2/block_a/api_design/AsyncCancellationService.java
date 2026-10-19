package pl.jakubtworek.backend_engineering.stage_2.block_a.api_design;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Operacja asynchroniczna jest osobnym zasobem. Przyjęcie komendy nie oznacza,
 * że efekt biznesowy już nastąpił.
 */
public final class AsyncCancellationService {

    private final OrderService orders;
    private final Clock clock;
    private final Supplier<UUID> idSupplier;
    private final Consumer<OrderResource> completionPublisher;
    private final Map<UUID, Operation> operations = new HashMap<>();

    public AsyncCancellationService(
            OrderService orders,
            Clock clock,
            Supplier<UUID> idSupplier,
            Consumer<OrderResource> completionPublisher
    ) {
        this.orders = Objects.requireNonNull(orders);
        this.clock = Objects.requireNonNull(clock);
        this.idSupplier = Objects.requireNonNull(idSupplier);
        this.completionPublisher = Objects.requireNonNull(completionPublisher);
    }

    public synchronized Operation start(UUID orderId) {
        orders.get(orderId);
        Operation operation = new Operation(idSupplier.get(), orderId, State.PENDING, clock.instant(), null);
        operations.put(operation.id(), operation);
        return operation;
    }

    public synchronized Operation get(UUID operationId) {
        Operation operation = operations.get(operationId);
        if (operation == null) {
            throw ApiFailure.notFound("Operation " + operationId);
        }
        return operation;
    }

    public synchronized Operation complete(UUID operationId) {
        Operation current = get(operationId);
        if (current.state() == State.SUCCEEDED) {
            return current;
        }
        OrderResource cancelled = orders.cancel(current.orderId());
        completionPublisher.accept(cancelled);
        Operation completed = new Operation(
                current.id(), current.orderId(), State.SUCCEEDED, current.acceptedAt(), clock.instant());
        operations.put(completed.id(), completed);
        return completed;
    }

    public enum State {
        PENDING,
        SUCCEEDED
    }

    public record Operation(UUID id, UUID orderId, State state, Instant acceptedAt, Instant completedAt) {
    }
}
