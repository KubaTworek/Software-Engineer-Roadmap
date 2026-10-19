package pl.jakubtworek.marketplace.process;

import java.util.EnumSet;
import java.util.Set;
import java.time.Instant;
import java.util.UUID;

/**
 * Trwały model procesu (process manager/saga) dla granicy wielu agregatów.
 *
 * Order nie może atomowo zmienić Payment i Inventory. Proces zapamiętuje więc
 * fakty, a nie „wywołuje transakcję rozproszoną”. Każda metoda jest idempotentna,
 * ponieważ broker może dostarczyć zdarzenie ponownie. Gdy jedna rezerwacja zawiedzie,
 * planowane są jawne kompensacje; kompensacja jest nową operacją biznesową, nie
 * cofnięciem czasu ani rollbackiem wcześniejszej transakcji.
 */
public final class OrderFulfillmentProcess {
    public enum Step {
        PAYMENT_RESERVED, PAYMENT_REJECTED,
        STOCK_RESERVED, STOCK_REJECTED,
        PAYMENT_RELEASE_REQUESTED, STOCK_RELEASE_REQUESTED
    }
    public enum Status { PENDING, CONFIRMED, COMPENSATING, CANCELLED }

    private final UUID orderId;
    private final Set<Step> completed = EnumSet.noneOf(Step.class);
    private Status status = Status.PENDING;
    private final Instant deadline;
    private long version;

    public OrderFulfillmentProcess(UUID orderId) {
        this(orderId, Instant.MAX);
    }

    public OrderFulfillmentProcess(UUID orderId, Instant deadline) {
        this(orderId, deadline, Status.PENDING, Set.of(), 0L);
    }

    private OrderFulfillmentProcess(
            UUID orderId,
            Instant deadline,
            Status status,
            Set<Step> completed,
            long version
    ) {
        this.orderId = java.util.Objects.requireNonNull(orderId);
        this.deadline = java.util.Objects.requireNonNull(deadline);
        this.status = java.util.Objects.requireNonNull(status);
        this.completed.addAll(completed);
        this.version = version;
    }

    /** Odtwarza sagę z trwałego snapshotu bez ponownego wykonywania przejść. */
    public static OrderFulfillmentProcess restore(
            UUID orderId,
            Instant deadline,
            Status status,
            Set<Step> completed,
            long version
    ) {
        return new OrderFulfillmentProcess(orderId, deadline, status, completed, version);
    }

    public Set<Step> paymentReserved() {
        if (terminal()) return Set.of();
        completed.add(Step.PAYMENT_RESERVED);
        if (completed.contains(Step.STOCK_REJECTED)) {
            boolean fresh = completed.add(Step.PAYMENT_RELEASE_REQUESTED);
            status = Status.COMPENSATING;
            return fresh ? Set.of(Step.PAYMENT_RELEASE_REQUESTED) : Set.of();
        } else {
            confirmWhenBothResourcesAreReserved();
            return Set.of();
        }
    }

    public Set<Step> stockReserved() {
        if (terminal()) return Set.of();
        completed.add(Step.STOCK_RESERVED);
        if (completed.contains(Step.PAYMENT_REJECTED)) {
            boolean fresh = completed.add(Step.STOCK_RELEASE_REQUESTED);
            status = Status.COMPENSATING;
            return fresh ? Set.of(Step.STOCK_RELEASE_REQUESTED) : Set.of();
        } else {
            confirmWhenBothResourcesAreReserved();
            return Set.of();
        }
    }

    public Set<Step> paymentRejected() {
        if (terminal()) return Set.of();
        completed.add(Step.PAYMENT_REJECTED);
        if (completed.contains(Step.STOCK_RESERVED)) {
            status = Status.COMPENSATING;
            completed.add(Step.STOCK_RELEASE_REQUESTED);
            return Set.of(Step.STOCK_RELEASE_REQUESTED);
        }
        cancelWhenBothSidesRejected();
        return Set.of();
    }

    public Set<Step> stockRejected() {
        if (terminal()) return Set.of();
        completed.add(Step.STOCK_REJECTED);
        if (completed.contains(Step.PAYMENT_RESERVED)) {
            status = Status.COMPENSATING;
            completed.add(Step.PAYMENT_RELEASE_REQUESTED);
            return Set.of(Step.PAYMENT_RELEASE_REQUESTED);
        }
        cancelWhenBothSidesRejected();
        return Set.of();
    }

    public void compensationCompleted() {
        if (status == Status.COMPENSATING) status = Status.CANCELLED;
    }

    /**
     * Timeout jest faktem procesu, nie wyjątkiem technicznym. Po deadline saga nie
     * może czekać bez końca na zagubione zdarzenie. Zwrócone kroki są komendami
     * kompensacyjnymi, które osobny adapter powinien opublikować przez outbox.
     */
    public Set<Step> expire(Instant now) {
        if (terminal() || now.isBefore(deadline)) return Set.of();
        status = Status.COMPENSATING;
        EnumSet<Step> requested = EnumSet.noneOf(Step.class);
        if (completed.contains(Step.PAYMENT_RESERVED)) requested.add(Step.PAYMENT_RELEASE_REQUESTED);
        if (completed.contains(Step.STOCK_RESERVED)) requested.add(Step.STOCK_RELEASE_REQUESTED);
        completed.addAll(requested);
        if (requested.isEmpty()) status = Status.CANCELLED;
        return Set.copyOf(requested);
    }

    private void confirmWhenBothResourcesAreReserved() {
        if (completed.containsAll(Set.of(Step.PAYMENT_RESERVED, Step.STOCK_RESERVED))) {
            status = Status.CONFIRMED;
        }
    }

    private void cancelWhenBothSidesRejected() {
        if (completed.containsAll(Set.of(Step.PAYMENT_REJECTED, Step.STOCK_REJECTED))) {
            status = Status.CANCELLED;
        }
    }

    private boolean terminal() {
        return status == Status.CONFIRMED || status == Status.CANCELLED;
    }

    public UUID orderId() { return orderId; }
    public Status status() { return status; }
    public Set<Step> completedSteps() { return Set.copyOf(completed); }
    public Instant deadline() { return deadline; }
    public long version() { return version; }
    public void persistedAs(long newVersion) { this.version = newVersion; }
}
