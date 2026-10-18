package pl.jakubtworek.marketplace.process;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.marketplace.shared.events.EventPublisher;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.Set;

/**
 * Aplikacyjna granica sagi. Każdy odebrany fakt jest zapisywany jako nowy snapshot
 * w tej samej transakcji co marker idempotencji konsumenta. Po restarcie decyzja
 * wynika z bazy, a nie z pamięci procesu JVM.
 */
@Service
public class OrderFulfillmentProcessManager {
    private final OrderFulfillmentProcessRepository repository;
    private final Duration timeout;
    private final EventPublisher eventPublisher;

    @Autowired
    public OrderFulfillmentProcessManager(
            OrderFulfillmentProcessRepository repository,
            @Value("${marketplace.fulfillment-process.timeout:PT15M}") Duration timeout,
            EventPublisher eventPublisher
    ) {
        this.repository = repository;
        this.timeout = timeout;
        this.eventPublisher = eventPublisher;
    }

    /** Konstruktor dla izolowanych testów modelu, w których komendy nie opuszczają procesu. */
    public OrderFulfillmentProcessManager(OrderFulfillmentProcessRepository repository, Duration timeout) {
        this(repository, timeout, ignored -> { });
    }

    @Transactional
    public void started(UUID orderId, Instant occurredAt) {
        if (repository.findByOrderId(orderId).isEmpty()) {
            repository.save(new OrderFulfillmentProcess(orderId, occurredAt.plus(timeout)));
        }
    }

    @Transactional
    public void paymentReserved(UUID orderId) { paymentReserved(orderId, UUID.randomUUID(), null); }
    public void paymentReserved(UUID orderId, UUID correlationId, UUID causationId) {
        mutate(orderId, OrderFulfillmentProcess::paymentReserved, correlationId, causationId);
    }

    @Transactional
    public void stockReserved(UUID orderId) { stockReserved(orderId, UUID.randomUUID(), null); }
    public void stockReserved(UUID orderId, UUID correlationId, UUID causationId) {
        mutate(orderId, OrderFulfillmentProcess::stockReserved, correlationId, causationId);
    }

    @Transactional
    public void paymentRejected(UUID orderId) { paymentRejected(orderId, UUID.randomUUID(), null); }
    public void paymentRejected(UUID orderId, UUID correlationId, UUID causationId) {
        mutate(orderId, OrderFulfillmentProcess::paymentRejected, correlationId, causationId);
    }

    @Transactional
    public void stockRejected(UUID orderId) { stockRejected(orderId, UUID.randomUUID(), null); }
    public void stockRejected(UUID orderId, UUID correlationId, UUID causationId) {
        mutate(orderId, OrderFulfillmentProcess::stockRejected, correlationId, causationId);
    }

    @Transactional
    public void compensationCompleted(UUID orderId) {
        mutate(orderId, process -> {
            process.compensationCompleted();
            return Set.of();
        }, UUID.randomUUID(), null);
    }

    @Transactional
    public int expireOverdue(int limit, Instant now) {
        var processes = repository.findExpiredPending(limit);
        processes.forEach(process -> {
            Set<OrderFulfillmentProcess.Step> commands = process.expire(now);
            repository.save(process);
            publishCompensations(process.orderId(), commands, UUID.randomUUID(), null);
        });
        return processes.size();
    }

    private void mutate(UUID orderId,
                        java.util.function.Function<OrderFulfillmentProcess, Set<OrderFulfillmentProcess.Step>> transition,
                        UUID correlationId, UUID causationId) {
        OrderFulfillmentProcess process = repository.findByOrderId(orderId)
                /*
                 * Fakty z różnych topiców nie mają globalnej kolejności. PaymentReserved
                 * może więc dotrzeć przed kopią OrderPlaced przeznaczoną dla process
                 * managera. Buforujemy taki fakt w tworzonym ad hoc procesie, zamiast
                 * wysyłać poprawne zdarzenie do DLQ. Późniejsze OrderPlaced jest wtedy
                 * idempotentnym potwierdzeniem istnienia sagi.
                 */
                .orElseGet(() -> new OrderFulfillmentProcess(orderId, Instant.now().plus(timeout)));
        Set<OrderFulfillmentProcess.Step> commands = transition.apply(process);
        repository.save(process);
        publishCompensations(orderId, commands, correlationId, causationId);
    }

    private void publishCompensations(UUID orderId, Set<OrderFulfillmentProcess.Step> commands,
                                      UUID correlationId, UUID causationId) {
        if (commands.contains(OrderFulfillmentProcess.Step.PAYMENT_RELEASE_REQUESTED)) {
            eventPublisher.publish(PaymentReleaseRequested.now(orderId, correlationId, causationId));
        }
        if (commands.contains(OrderFulfillmentProcess.Step.STOCK_RELEASE_REQUESTED)) {
            eventPublisher.publish(StockReleaseRequested.now(orderId, correlationId, causationId));
        }
    }
}
