package pl.jakubtworek.backend.order.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

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

    public OrderService(OrderRepository repository,
                        PaymentClient paymentClient,
                        ReservationClient reservationClient, OutboxRepository outboxRepository,
                        ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.paymentClient = paymentClient;
        this.reservationClient = reservationClient;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * Tworzy zamówienie dla istniejącej rezerwacji.
     *
     * Metoda jest transakcyjna, bo zapisuje i aktualizuje stan OrderEntity.
     *
     * Stan zamówienia oraz wiadomość outbox są zapisywane w jednej transakcji lokalnej.
     * Relay publikuje później co najmniej raz, więc konsument nadal musi obsłużyć duplikaty.
     */
    @Transactional
    public OrderResponse create(CreateOrderRequest request, String idempotencyKey) {
        /*
         * Idempotency key chroni przed utworzeniem duplikatu zamówienia.
         *
         * Typowy scenariusz:
         *
         * 1. Klient wysyła POST /orders.
         * 2. Order Service przetwarza płatność.
         * 3. Klient dostaje timeout albo traci połączenie.
         * 4. Klient ponawia ten sam request z tym samym Idempotency-Key.
         * 5. Serwis zwraca istniejące zamówienie zamiast tworzyć nowe.
         */
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            repository.lockIdempotencyKey(idempotencyKey);
            var existing = repository.findByIdempotencyKey(idempotencyKey);

            if (existing.isPresent()) {
                String fingerprint = fingerprint(request);
                /* Rekord utworzony przed migracją V2 nie ma fingerprintu; okres expand
                 * zachowuje odczyt starego formatu do czasu bezpiecznego backfillu. */
                if (existing.get().getRequestFingerprint() != null
                        && !fingerprint.equals(existing.get().getRequestFingerprint())) {
                    throw new IllegalArgumentException("Idempotency-Key was already used with a different request");
                }
                log.info("idempotent_order_replayed orderId={} idempotencyKey={}",
                        existing.get().getId(), idempotencyKey);

                return toResponse(existing.get());
            }
        }

        /*
         * Pobieramy rezerwację z Reservation Service.
         *
         * Order Service nie powinien ufać tylko danym z requestu klienta.
         * Musi sprawdzić, czy rezerwacja istnieje i czy należy do użytkownika,
         * który próbuje utworzyć zamówienie.
         */
        ReservationClient.ReservationResponse reservation = reservationClient.get(request.reservationId());

        /*
         * Defensive check.
         *
         * W normalnym przypadku klient HTTP powinien albo zwrócić poprawny obiekt,
         * albo rzucić wyjątek. Jeśli jednak dostaniemy null, traktujemy to jako błąd zależności.
         */
        if (reservation == null) {
            throw new IllegalStateException("Reservation service returned empty response");
        }

        /*
         * Sprawdzamy ownership rezerwacji.
         *
         * Użytkownik nie powinien móc opłacić cudzej rezerwacji.
         */
        if (!request.userId().equals(reservation.userId())) {
            throw new IllegalArgumentException("Reservation belongs to a different user");
        }

        /*
         * Zamówienie można utworzyć tylko dla rezerwacji w stanie PENDING.
         *
         * Jeśli rezerwacja jest już CONFIRMED, EXPIRED albo CANCELLED, ponowna płatność
         * byłaby błędem biznesowym.
         */
        if (!"PENDING".equals(reservation.status())) {
            throw new IllegalArgumentException(
                    "Reservation must be PENDING before payment. Current status: " + reservation.status()
            );
        }

        /*
         * Tworzymy zamówienie w stanie PENDING przed wywołaniem płatności.
         *
         * Dzięki temu mamy trwały zapis próby zamówienia nawet wtedy, gdy payment-service
         * odpowie wolno, zwróci błąd albo będzie niedostępny.
         */
        OrderEntity order = repository.save(
                OrderEntity.pending(request.reservationId(), request.userId(), idempotencyKey,
                        fingerprint(request), clock.instant())
        );

        log.info("order_created orderId={} reservationId={} userId={}",
                order.getId(), order.getReservationId(), order.getUserId());

        order.markPaymentInProgress();
        try {
            /*
             * Wywołanie payment-mock-service.
             *
             * W tym miejscu mogą zadziałać:
             *
             * - timeout,
             * - retry/backoff,
             * - circuit breaker,
             * - fallback.
             *
             * Jeśli paymentClient rzuci wyjątek, przejdziemy do graceful degradation poniżej.
             */
            paymentClient.pay(order.getId(), order.getUserId(), order.getAmount());

            /*
             * Po udanej płatności potwierdzamy rezerwację.
             *
             * To jest ważne, bo samo opłacenie zamówienia nie powinno zostawić rezerwacji
             * w stanie PENDING.
             */
        } catch (Exception exception) {
            /*
             * Timeout jest wynikiem niejednoznacznym: provider mógł obciążyć kartę, a jego
             * odpowiedź zginęła. FAILED byłoby kłamstwem; PAYMENT_UNKNOWN wymusza lookup
             * po idempotency key przez proces rekoncyliacji.
             */
            order.markPaymentUnknown(exception.getMessage());
            order.scheduleReconciliation(exception.getMessage(), clock.instant());
            return toResponse(order);
        }

        ReservationClient.ReservationResponse confirmedReservation;
        try {
            confirmedReservation = reservationClient.confirm(order.getReservationId());

            /*
             * Oznaczamy zamówienie jako opłacone.
             */
            order.markPaid();

            /*
             * Zapisujemy intencję publikacji eventu order.paid.
             *
             * Notification Service konsumuje ten event i może wysłać mail/SMS/powiadomienie.
             *
             * Do eventu dokładamy correlationId/requestId/traceId z MDC, żeby asynchroniczny
             * przepływ przez brokera nadal dało się powiązać z requestem HTTP.
             */
            OrderPaidEvent event = OrderPaidEvent.at(
                            confirmedReservation.eventId(),
                            order.getId(),
                            order.getReservationId(),
                            order.getUserId(),
                            order.getAmount(),
                            clock.instant(),
                            MDC.get(CorrelationId.MDC_CORRELATION_ID),
                            MDC.get(CorrelationId.MDC_REQUEST_ID),
                            MDC.get(CorrelationId.MDC_TRACE_ID));
            outboxRepository.save(new OutboxMessage(UUID.randomUUID(), order.getId().toString(),
                    "order.paid.v1", serialize(event), clock.instant()));

            log.info("order_paid orderId={} reservationId={}",
                    order.getId(), order.getReservationId());
        } catch (Exception exception) {
            /*
             * Graceful degradation.
             *
             * Płatność jest już znana jako udana. Awaria tego kroku oznacza więc oczekiwanie
             * na potwierdzenie rezerwacji, a nie nieznany wynik płatności.
             *
             * To daje możliwość późniejszego retry/reconciliation bez utraty zamówienia.
             *
             * Trade-off:
             * Użytkownik nie dostaje natychmiastowej finalnej odpowiedzi "PAID",
             * ale system nie gubi intencji zakupu.
             */
            log.warn("order_reservation_confirmation_pending orderId={} reservationId={} reason={}",
                    order.getId(), order.getReservationId(), exception.getMessage());

            order.markReservationConfirmationPending(exception.getMessage());
            order.scheduleReconciliation(exception.getMessage(), clock.instant());
        }

        /*
         * Zwracamy aktualny stan zamówienia:
         *
         * - PAID, jeśli płatność i potwierdzenie rezerwacji się udały,
         * - PAYMENT_UNKNOWN albo RESERVATION_CONFIRMATION_PENDING przy częściowym sukcesie.
         */
        return toResponse(order);
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
    @Transactional
    public void reconcile(UUID id) {
        OrderEntity order = repository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + id));
        if (order.getStatus() == OrderStatus.PAID || order.getStatus() == OrderStatus.FAILED
                || order.getStatus() == OrderStatus.MANUAL_REVIEW) {
            return;
        }
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
            ReservationClient.ReservationResponse reservation = reservationClient.confirm(order.getReservationId());
            order.markPaid();
            OrderPaidEvent event = OrderPaidEvent.at(reservation.eventId(), order.getId(), order.getReservationId(),
                    order.getUserId(), order.getAmount(), clock.instant(), null, null, null);
            outboxRepository.save(new OutboxMessage(UUID.randomUUID(), order.getId().toString(),
                    "order.paid.v1", serialize(event), clock.instant()));
        } catch (Exception exception) {
            order.scheduleReconciliation(exception.getMessage(), clock.instant());
        }
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
