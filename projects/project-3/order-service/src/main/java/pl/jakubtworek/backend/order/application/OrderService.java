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

@Service
public class OrderService {
    private static final Logger log = LoggerFactory.getLogger(OrderService.class);
    private final OrderRepository repository;
    private final PaymentClient paymentClient;
    private final ReservationClient reservationClient;
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

    @Transactional
    public OrderResponse create(CreateOrderRequest request, String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            var existing = repository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                log.info("idempotent_order_replayed orderId={} idempotencyKey={}", existing.get().getId(), idempotencyKey);
                return toResponse(existing.get());
            }
        }

        ReservationClient.ReservationResponse reservation = reservationClient.get(request.reservationId());
        if (reservation == null) {
            throw new IllegalStateException("Reservation service returned empty response");
        }
        if (!request.userId().equals(reservation.userId())) {
            throw new IllegalArgumentException("Reservation belongs to a different user");
        }
        if (!"PENDING".equals(reservation.status())) {
            throw new IllegalArgumentException("Reservation must be PENDING before payment. Current status: " + reservation.status());
        }

        OrderEntity order = repository.save(OrderEntity.pending(request.reservationId(), request.userId(), idempotencyKey));
        log.info("order_created orderId={} reservationId={} userId={}", order.getId(), order.getReservationId(), order.getUserId());
        try {
            paymentClient.pay(order.getId(), order.getUserId(), order.getAmount());
            ReservationClient.ReservationResponse confirmedReservation = reservationClient.confirm(order.getReservationId());
            order.markPaid();
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
            log.info("order_paid orderId={} reservationId={}", order.getId(), order.getReservationId());
        } catch (Exception exception) {
            log.warn("order_degraded_to_payment_pending orderId={} reservationId={} reason={}", order.getId(), order.getReservationId(), exception.getMessage());
            order.markPaymentPending(exception.getMessage());
        }
        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public OrderResponse get(UUID id) {
        return repository.findById(id).map(this::toResponse)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + id));
    }

    private OrderResponse toResponse(OrderEntity order) {
        return new OrderResponse(order.getId(), order.getReservationId(), order.getUserId(), order.getAmount(), order.getStatus(), order.getCreatedAt(), order.getDegradationReason());
    }
}
