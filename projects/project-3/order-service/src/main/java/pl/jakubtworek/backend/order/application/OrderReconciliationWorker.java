package pl.jakubtworek.backend.order.application;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pl.jakubtworek.backend.order.domain.OrderStatus;
import pl.jakubtworek.backend.order.repository.OrderRepository;

import java.time.Clock;
import java.util.List;

/**
 * Liveness uzupełnia safety: system nie deklaruje fałszywego sukcesu, ale okresowo
 * doprowadza częściowy workflow do stanu terminalnego. nextAttemptAt i wykładniczy
 * backoff zapobiegają retry storm podczas długiej awarii downstreamu.
 */
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "app.reconciliation.enabled", matchIfMissing = true)
public class OrderReconciliationWorker {
    private final OrderRepository repository;
    private final OrderService service;
    private final Clock clock;

    public OrderReconciliationWorker(OrderRepository repository, OrderService service, Clock clock) {
        this.repository = repository;
        this.service = service;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${app.reconciliation.poll-delay:2s}")
    public void run() {
        repository.findTop100ByStatusInAndNextAttemptAtBeforeOrderByNextAttemptAtAsc(
                List.of(OrderStatus.PENDING, OrderStatus.PAYMENT_IN_PROGRESS,
                        OrderStatus.PAYMENT_UNKNOWN, OrderStatus.RESERVATION_CONFIRMATION_PENDING,
                        OrderStatus.COMPENSATION_PENDING), clock.instant())
                .forEach(order -> {
                    try { service.reconcile(order.getId()); }
                    catch (RuntimeException failed) {
                        // Błąd commitu jednej sagi nie blokuje postępu pozostałych.
                        System.getLogger(getClass().getName()).log(System.Logger.Level.WARNING,
                                "Reconciliation failed for order " + order.getId(), failed);
                    }
                });
    }
}
