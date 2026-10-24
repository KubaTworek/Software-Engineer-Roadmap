package pl.jakubtworek.backend.order.application;

import org.slf4j.MDC;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import pl.jakubtworek.backend.common.events.OrderPaidEvent;
import pl.jakubtworek.backend.common.web.CorrelationId;
import pl.jakubtworek.backend.order.api.CreateOrderRequest;
import pl.jakubtworek.backend.order.api.OrderResponse;
import pl.jakubtworek.backend.order.client.PaymentClient;
import pl.jakubtworek.backend.order.client.ReservationClient;
import pl.jakubtworek.backend.order.outbox.OutboxMessage;
import pl.jakubtworek.backend.order.outbox.OutboxRepository;
import pl.jakubtworek.backend.order.domain.OrderEntity;
import pl.jakubtworek.backend.order.repository.OrderRepository;

import java.util.UUID;
import java.time.Clock;
import java.nio.charset.StandardCharsets;
import pl.jakubtworek.backend.order.domain.OrderStatus;

/**
 * Warstwa aplikacyjna Order Service.
 *
 * Ten serwis odpowiada za proces tworzenia zamówienia:
 *
 * 1. obsługę idempotency key,
 * 2. pobranie i walidację rezerwacji,
 * 3. utworzenie zamówienia w stanie PENDING,
 * 4. próbę wykonania płatności,
 * 5. potwierdzenie rezerwacji po udanej płatności,
 * 6. oznaczenie zamówienia jako PAID,
 * 7. atomowy zapis eventu OrderPaidEvent do outboxa,
 * 8. rozróżnienie nieznanej płatności od oczekiwania na potwierdzenie rezerwacji.
 *
 * To jest jedna z ważniejszych klas projektu, bo pokazuje typowy workflow mikroserwisowy:
 *
 * order-service
 *   -> reservation-service
 *   -> payment-mock-service
 *   -> rabbitmq
 *      -> notification-service
 */
@Service
public class OrderService {

    /**
     * Repozytorium zamówień.
     *
     * Order Service posiada własną bazę danych. Nie zapisuje zamówień w bazie
     * reservation-service ani catalog-service. To jest zgodne z zasadą database-per-service.
     */
    private final OrderRepository repository;

    /**
     * Klient HTTP do payment-mock-service.
     *
     * PaymentClient powinien zawierać mechanizmy resilience, np.:
     *
     * - timeout,
     * - retry/backoff,
     * - circuit breaker.
     */
    private final PaymentClient paymentClient;

    /**
     * Klient HTTP do reservation-service.
     *
     * Używany do:
     *
     * - pobrania rezerwacji,
     * - potwierdzenia rezerwacji po udanej płatności.
     */
    private final ReservationClient reservationClient;

    /**
     * Outbox oddziela lokalną transakcję DB od zawodnej publikacji do RabbitMQ.
     */
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public OrderService(OrderRepository repository,
                        PaymentClient paymentClient,
                        ReservationClient reservationClient, OutboxRepository outboxRepository,
                        ObjectMapper objectMapper, Clock clock, PlatformTransactionManager transactions) {
        this.repository = repository;
        this.paymentClient = paymentClient;
        this.reservationClient = reservationClient;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactions);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Intencja musi zostać COMMITnięta PRZED pierwszym efektem u providera.
     * save()/flush() w transakcji obejmującej HTTP nie zapewnia takiej trwałości. */
    public OrderResponse create(CreateOrderRequest request, String idempotencyKey) {
        String key = idempotencyKey == null || idempotencyKey.isBlank()
                ? "reservation:" + request.reservationId() : idempotencyKey;
        UUID id = transaction.execute(tx -> prepare(request, key).getId());
        reconcile(id);
        return get(id);
    }

    private OrderEntity prepare(CreateOrderRequest request, String key) {
        // Stała kolejność blokad: zasób, potem klucz. Różne klucze nie mogą opłacić
        // tej samej rezerwacji dwa razy; constraint jest drugą linią obrony.
        repository.lockIdempotencyKey("reservation:" + request.reservationId());
        repository.lockIdempotencyKey("request:" + key);
        var existing = repository.findByIdempotencyKey(key);
        if (existing.isEmpty()) existing = repository.findByReservationId(request.reservationId());
        if (existing.isPresent()) {
            OrderEntity order = existing.get();
            // Nie przyjmujemy kolejnego klucza bez trwałego związania go z requestem.
            // Inaczej taki "alias" mógłby później utworzyć zamówienie dla innego zasobu.
            if (order.getIdempotencyKey() != null && !key.equals(order.getIdempotencyKey()))
                throw new IllegalArgumentException("Reservation already has an order; repeat its original Idempotency-Key");
            if (!request.userId().equals(order.getUserId())
                    || !request.reservationId().equals(order.getReservationId())
                    || (order.getRequestFingerprint() != null
                        && !fingerprint(request).equals(order.getRequestFingerprint()))) {
                throw new IllegalArgumentException("Idempotency-Key was already used with a different request");
            }
            // Najpierw trwała intencja: po utracie commitu rezerwacja może być już CONFIRMED.
            return order;
        }
        var reservation = reservationClient.get(request.reservationId());
        if (reservation == null) throw new IllegalStateException("Reservation service returned empty response");
        if (!request.userId().equals(reservation.userId()))
            throw new IllegalArgumentException("Reservation belongs to a different user");
        if (!"PENDING".equals(reservation.status()))
            throw new IllegalArgumentException("Reservation must be PENDING before payment");
        OrderEntity order = OrderEntity.pending(request.reservationId(), request.userId(), key,
                fingerprint(request), clock.instant());
        order.queueForProcessing(clock.instant());
        return repository.save(order);
    }
    /**
     * Pobiera zamówienie po ID.
     *
     * readOnly = true sygnalizuje, że metoda nie powinna modyfikować danych.
     * Może to pomóc JPA/Hibernate w optymalizacji pracy persistence context.
     */
    @Transactional(readOnly = true)
    public OrderResponse get(UUID id) {
        return repository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + id));
    }

    /**
     * Domyka częściowy sukces bez ponownego POST płatności w ciemno. Blokada wiersza
     * serializuje repliki workera; lookup providera rozstrzyga niepewny timeout.
     */
    public void reconcile(UUID id) {
        transaction.executeWithoutResult(tx -> reconcileLocked(id));
    }

    private void reconcileLocked(UUID id) {
        OrderEntity order = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + id));
        if (order.getStatus() == OrderStatus.PAID || order.getStatus() == OrderStatus.FAILED
                || order.getStatus() == OrderStatus.MANUAL_REVIEW) {
            return;
        }
        // Blokada wiersza serializuje workerów. Transakcja wykonawcza może się wycofać,
        // lecz intencja pozostaje PENDING; powtórka użyje tego samego orderId u providera.
        if (order.getStatus() == OrderStatus.PENDING || order.getStatus() == OrderStatus.PAYMENT_IN_PROGRESS) {
            order.markPaymentInProgress();
            try {
                paymentClient.pay(order.getId(), order.getUserId(), order.getAmount());
                order.markReservationConfirmationPending("Payment accepted");
            } catch (Exception uncertain) {
                order.markPaymentUnknown(uncertain.getMessage());
                order.scheduleReconciliation(uncertain.getMessage(), clock.instant());
                return;
            }
        }
        ReservationClient.ReservationResponse reservation;
        try {
            if (order.getStatus() == OrderStatus.PAYMENT_UNKNOWN) {
                if (order.getReconciliationAttempts() >= 10) {
                    order.markManualReview("Payment result remained unknown after retry budget was exhausted");
                    return;
                }
                PaymentClient.PaymentResponse payment = paymentClient.findByOrder(order.getId());
                if (payment == null || !"PAID".equals(payment.status())) {
                    order.scheduleReconciliation("Payment is not terminal", clock.instant());
                    return;
                }
                order.markReservationConfirmationPending("Payment confirmed; reservation confirmation required");
            }

            /* Płatność jest potwierdzona, ale zasobu nie udało się zarezerwować. Po pięciu
             * próbach przechodzimy do jawnej kompensacji zamiast generować retry storm. */
            if (order.getStatus() == OrderStatus.RESERVATION_CONFIRMATION_PENDING
                    && order.getReconciliationAttempts() >= 5) {
                order.markCompensationPending("Reservation could not be confirmed; refund required", clock.instant());
            }
            if (order.getStatus() == OrderStatus.COMPENSATION_PENDING) {
                PaymentClient.PaymentResponse refund = paymentClient.refund(order.getId());
                if (refund == null || !"REFUNDED".equals(refund.status())) {
                    throw new IllegalStateException("Payment provider did not confirm refund");
                }
                order.markFailed("Reservation failed and payment was refunded");
                return;
            }
            reservation = reservationClient.confirm(order.getReservationId());
            order.markPaid();
        } catch (Exception exception) {
            order.scheduleReconciliation(exception.getMessage(), clock.instant());
            return;
        }
        OrderPaidEvent event = OrderPaidEvent.at(reservation.eventId(), order.getId(), order.getReservationId(),
                    order.getUserId(), order.getAmount(), clock.instant(),
                    MDC.get(CorrelationId.MDC_CORRELATION_ID), MDC.get(CorrelationId.MDC_REQUEST_ID),
                    MDC.get(CorrelationId.MDC_TRACE_ID));
            outboxRepository.save(new OutboxMessage(UUID.randomUUID(), order.getId().toString(),
                    "order.paid.v1", serialize(event), clock.instant()));
    }

    /**
     * Mapuje encję domenową/persistence na DTO odpowiedzi API.
     *
     * Nie zwracamy OrderEntity bezpośrednio z kontrolera, żeby nie mieszać modelu bazy danych
     * z kontraktem HTTP.
     */
    private OrderResponse toResponse(OrderEntity order) {
        return new OrderResponse(
                order.getId(),
                order.getReservationId(),
                order.getUserId(),
                order.getAmount(),
                order.getStatus(),
                order.getCreatedAt(),
                order.getDegradationReason()
        );
    }

    private String fingerprint(CreateOrderRequest request) {
        String canonical = request.reservationId() + ":" + request.userId();
        return UUID.nameUUIDFromBytes(canonical.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private String serialize(OrderPaidEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot serialize order event", exception);
        }
    }
}
