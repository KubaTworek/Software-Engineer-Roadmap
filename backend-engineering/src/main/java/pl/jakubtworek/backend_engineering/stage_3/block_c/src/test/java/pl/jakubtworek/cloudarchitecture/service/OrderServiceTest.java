package pl.jakubtworek.cloudarchitecture.service;

import org.junit.jupiter.api.Test;
import pl.jakubtworek.cloudarchitecture.dto.CreateOrderRequest;
import pl.jakubtworek.cloudarchitecture.dto.OrderCreatedResponse;
import pl.jakubtworek.cloudarchitecture.entity.OrderEntity;
import pl.jakubtworek.cloudarchitecture.entity.OutboxEventEntity;
import pl.jakubtworek.cloudarchitecture.repository.OrderRepository;
import pl.jakubtworek.cloudarchitecture.repository.OutboxEventRepository;
import pl.jakubtworek.cloudarchitecture.service.OrderService;

import java.util.List;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-30T12:00:00Z");

    @Test
    void storesOrderAndOutboxEventInTheSameServiceTransaction() {
        OrderRepository repository = mock(OrderRepository.class);
        OutboxEventRepository outboxRepository = mock(OutboxEventRepository.class);
        OrderEntity savedOrder = mock(OrderEntity.class);
        when(savedOrder.getId()).thenReturn(42L);
        when(repository.save(any(OrderEntity.class))).thenReturn(savedOrder);

        OrderCreatedResponse response = new OrderService(
                repository,
                outboxRepository,
                new ObjectMapper(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        ).createOrder(
                new CreateOrderRequest("customer-1", List.of(10L))
        );

        assertThat(response).isEqualTo(new OrderCreatedResponse(42L, "ACCEPTED"));
        verify(repository).save(argThat(order ->
                order.getCustomerId().equals("customer-1")
                        && order.getCreatedAt().equals(NOW)
        ));
        verify(outboxRepository).save(argThat(event ->
                event.getAggregateId().equals(42L)
                        && event.getEventType().equals("ORDER_CREATED")
                        && event.getPayload().equals("{\"orderId\":42}")
                        && event.getCreatedAt().equals(NOW)
                        && event.getPublishedAt() == null
        ));
    }
}
