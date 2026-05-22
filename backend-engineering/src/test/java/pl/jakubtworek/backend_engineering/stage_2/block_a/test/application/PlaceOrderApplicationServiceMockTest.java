package pl.jakubtworek.backend_engineering.stage_2.block_a.test.application;

import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.application.command.PlaceOrderCommand;
import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.application.command.PlaceOrderLineCommand;
import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.application.port.DomainEventPublisher;
import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.application.port.OrderRepository;
import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.application.port.TransactionManager;
import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.application.service.PlaceOrderApplicationService;
import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.domain.event.OrderPlacedEvent;
import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.domain.model.Order;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// Application service test using mocks.
// This style focuses on interaction verification rather than in-memory state.
class PlaceOrderApplicationServiceMockTest {

    @Test
    void shouldSaveOrderAndPublishOrderPlacedEvent() {
        // Given
        OrderRepository orderRepository = mock(OrderRepository.class);
        DomainEventPublisher eventPublisher = mock(DomainEventPublisher.class);
        TransactionManager transactionManager = action -> action.run();

        PlaceOrderApplicationService service = new PlaceOrderApplicationService(
                orderRepository,
                eventPublisher,
                transactionManager
        );

        PlaceOrderCommand command = new PlaceOrderCommand(
                "C-456",
                "PLN",
                List.of(
                        new PlaceOrderLineCommand(
                                "P-1",
                                2,
                                new BigDecimal("50.00")
                        )
                ),
                new BigDecimal("100.00")
        );

        // When
        service.placeOrder(command);

        // Then
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(orderCaptor.capture());

        Order savedOrder = orderCaptor.getValue();

        assertEquals("C-456", savedOrder.customerId().value());

        ArgumentCaptor<OrderPlacedEvent> eventCaptor =
                ArgumentCaptor.forClass(OrderPlacedEvent.class);

        verify(eventPublisher).publish(eventCaptor.capture());

        OrderPlacedEvent event = eventCaptor.getValue();

        assertEquals(savedOrder.id(), event.orderId());
        assertEquals(savedOrder.total(), event.total());
    }
}