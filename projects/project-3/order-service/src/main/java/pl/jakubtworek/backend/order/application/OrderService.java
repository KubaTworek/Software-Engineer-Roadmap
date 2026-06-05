package pl.jakubtworek.backend.order.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.backend.common.events.OrderPaidEvent;
import pl.jakubtworek.backend.common.web.CorrelationId;
import pl.jakubtworek.backend.order.api.CreateOrderRequest;
import pl.jakubtworek.backend.order.api.OrderResponse;
import pl.jakubtworek.backend.order.client.PaymentClient;
import pl.jakubtworek.backend.order.client.ReservationClient;
import pl.jakubtworek.backend.order.config.RabbitConfig;
import pl.jakubtworek.backend.order.domain.OrderEntity;
import pl.jakubtworek.backend.order.repository.OrderRepository;

import java.util.UUID;

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
 * 7. publikację eventu OrderPaidEvent,
 * 8. degradację do PAYMENT_PENDING, jeśli downstream zawiedzie.
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
     * RabbitTemplate służy do publikowania eventów domenowych do RabbitMQ.
     *
     * W tym serwisie publikujemy OrderPaidEvent po skutecznej płatności i potwierdzeniu rezerwacji.
     */
    private final RabbitTemplate rabbitTemplate;

    public OrderService(OrderRepository repository,
                        PaymentClient paymentClient,
                        ReservationClient reservationClient,
                        RabbitTemplate rabbitTemplate) {
        this.repository = repository;
        this.paymentClient = paymentClient;
        this.reservationClient = reservationClient;
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * Tworzy zamówienie dla istniejącej rezerwacji.
     *
     * Metoda jest transakcyjna, bo zapisuje i aktualizuje stan OrderEntity.
     *
     * Uwaga projektowa:
     * W tej wersji publikacja eventu do RabbitMQ dzieje się wewnątrz tej samej metody.
     * Dla systemu produkcyjnego lepszy byłby Outbox Pattern, bo obecnie istnieje ryzyko:
     *
     * - zamówienie zostanie zapisane jako PAID,
     * - ale publikacja eventu do brokera się nie powiedzie,
     * - albo odwrotnie: event zostanie opublikowany, a transakcja DB później się wycofa.
     *
     * Na potrzeby projektu edukacyjnego to jest akceptowalny baseline, ale warto znać ten trade-off.
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
            var existing = repository.findByIdempotencyKey(idempotencyKey);

            if (existing.isPresent()) {
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
                OrderEntity.pending(request.reservationId(), request.userId(), idempotencyKey)
        );

        log.info("order_created orderId={} reservationId={} userId={}",
                order.getId(), order.getReservationId(), order.getUserId());

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
            ReservationClient.ReservationResponse confirmedReservation =
                    reservationClient.confirm(order.getReservationId());

            /*
             * Oznaczamy zamówienie jako opłacone.
             */
            order.markPaid();

            /*
             * Publikujemy event domenowy order.paid.
             *
             * Notification Service konsumuje ten event i może wysłać mail/SMS/powiadomienie.
             *
             * Do eventu dokładamy correlationId/requestId/traceId z MDC, żeby asynchroniczny
             * przepływ przez brokera nadal dało się powiązać z requestem HTTP.
             */
            rabbitTemplate.convertAndSend(
                    RabbitConfig.ORDERS_EXCHANGE,
                    RabbitConfig.ORDER_PAID_ROUTING_KEY,
                    OrderPaidEvent.now(
                            confirmedReservation.eventId(),
                            order.getId(),
                            order.getReservationId(),
                            order.getUserId(),
                            order.getAmount(),
                            MDC.get(CorrelationId.MDC_CORRELATION_ID),
                            MDC.get(CorrelationId.MDC_REQUEST_ID),
                            MDC.get(CorrelationId.MDC_TRACE_ID)
                    )
            );

            log.info("order_paid orderId={} reservationId={}",
                    order.getId(), order.getReservationId());
        } catch (Exception exception) {
            /*
             * Graceful degradation.
             *
             * Jeśli payment-service, reservation-service albo RabbitMQ zawiedzie,
             * nie oznaczamy zamówienia od razu jako FAILED. Zamiast tego przechodzimy
             * do PAYMENT_PENDING.
             *
             * To daje możliwość późniejszego retry/reconciliation bez utraty zamówienia.
             *
             * Trade-off:
             * Użytkownik nie dostaje natychmiastowej finalnej odpowiedzi "PAID",
             * ale system nie gubi intencji zakupu.
             */
            log.warn("order_degraded_to_payment_pending orderId={} reservationId={} reason={}",
                    order.getId(), order.getReservationId(), exception.getMessage());

            order.markPaymentPending(exception.getMessage());
        }

        /*
         * Zwracamy aktualny stan zamówienia:
         *
         * - PAID, jeśli płatność i potwierdzenie rezerwacji się udały,
         * - PAYMENT_PENDING, jeśli nastąpiła degradacja.
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
}