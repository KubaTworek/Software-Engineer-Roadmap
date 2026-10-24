package pl.jakubtworek.backend.order.application;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pl.jakubtworek.backend.order.client.PaymentClient;
import pl.jakubtworek.backend.order.client.ReservationClient;
import pl.jakubtworek.backend.order.domain.OrderEntity;
import pl.jakubtworek.backend.order.domain.OrderStatus;
import pl.jakubtworek.backend.order.outbox.OutboxMessage;
import pl.jakubtworek.backend.order.outbox.OutboxRepository;
import pl.jakubtworek.backend.order.repository.OrderRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Testuje decyzje sagi, których nie da się wiarygodnie udowodnić samym happy pathem HTTP.
 * Mockowane są granice sieciowe, ale stan przechodzi przez prawdziwy model domenowy.
 */
class OrderServiceReconciliationTest {
    private static final Instant NOW = Instant.parse("2026-09-13T10:00:00Z");

    private OrderRepository orders;
    private PaymentClient payments;
    private ReservationClient reservations;
    private OutboxRepository outbox;
    private OrderService service;

    @BeforeEach
    void setUp() {
        orders = mock(OrderRepository.class);
        payments = mock(PaymentClient.class);
        reservations = mock(ReservationClient.class);
        outbox = mock(OutboxRepository.class);
        var tx = mock(org.springframework.transaction.PlatformTransactionManager.class);
        when(tx.getTransaction(any())).thenAnswer(call -> new org.springframework.transaction.support.SimpleTransactionStatus());
        service = new OrderService(orders, payments, reservations, outbox, new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC), tx);
    }

    @Test
    void resolvesUnknownPaymentByLookupWithoutChargingAgain() {
        OrderEntity order = pendingOrder();
        order.markPaymentUnknown("response timeout");
        order.scheduleReconciliation("response timeout", NOW.minusSeconds(10));
        when(orders.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(payments.findByOrder(order.getId()))
                .thenReturn(new PaymentClient.PaymentResponse(UUID.randomUUID(), order.getId(), "PAID"));
        when(reservations.confirm(order.getReservationId())).thenReturn(reservation(order));
        when(reservations.get(order.getReservationId())).thenReturn(reservation(order));

        service.reconcile(order.getId());

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        verify(payments, never()).pay(any(), any(), any());
        verify(outbox).save(any(OutboxMessage.class));
    }

    @Test
    void refundsPaidOrderAfterReservationRetryBudgetIsExhausted() {
        OrderEntity order = pendingOrder();
        order.markReservationConfirmationPending("reservation unavailable");
        for (int attempt = 0; attempt < 5; attempt++) {
            order.scheduleReconciliation("reservation unavailable", NOW.minusSeconds(60));
        }
        when(orders.findByIdForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(payments.refund(order.getId()))
                .thenReturn(new PaymentClient.PaymentResponse(UUID.randomUUID(), order.getId(), "REFUNDED"));

        service.reconcile(order.getId());

        assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(order.getDegradationReason()).contains("refunded");
        verify(reservations, never()).confirm(any());
        verify(outbox, never()).save(any());
    }

    private OrderEntity pendingOrder() {
        return OrderEntity.pending(UUID.randomUUID(), "user-1", "order-key", "fingerprint", NOW);
    }

    private ReservationClient.ReservationResponse reservation(OrderEntity order) {
        return new ReservationClient.ReservationResponse(order.getReservationId(), UUID.randomUUID(),
                order.getUserId(), 1, "CONFIRMED", NOW.plusSeconds(60), NOW, NOW);
    }
}
