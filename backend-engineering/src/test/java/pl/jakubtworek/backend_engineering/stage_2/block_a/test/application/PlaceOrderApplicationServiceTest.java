package pl.jakubtworek.backend_engineering.stage_2.block_a.test.application;

import org.junit.jupiter.api.Test;
import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.application.command.PlaceOrderCommand;
import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.application.command.PlaceOrderLineCommand;
import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.application.service.PlaceOrderApplicationService;
import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.domain.event.OrderPlacedEvent;
import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.domain.model.Order;
import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.domain.model.OrderId;
import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.domain.model.OrderStatus;
import pl.jakubtworek.backend_engineering.stage_2.block_a.test.application.testdouble.FakeDomainEventPublisher;
import pl.jakubtworek.backend_engineering.stage_2.block_a.test.application.testdouble.FakeOrderRepository;
import pl.jakubtworek.backend_engineering.stage_2.block_a.test.application.testdouble.FakeTransactionManager;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Unit test for the application service.
// It verifies orchestration using fake ports instead of infrastructure.
class PlaceOrderApplicationServiceTest {

    @Test
    void shouldPlaceOrderSaveAggregateAndPublishEvent() {
        // Given
        FakeOrderRepository orderRepository = new FakeOrderRepository();
        FakeDomainEventPublisher eventPublisher = new FakeDomainEventPublisher();
        FakeTransactionManager transactionManager = new FakeTransactionManager();

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
        OrderId orderId = service.placeOrder(command);

        // Then
        assertNotNull(orderId);
        assertEquals(1, transactionManager.transactionCount());
        assertEquals(1, orderRepository.saveCount());

        Order savedOrder = orderRepository.lastSavedOrder();

        assertNotNull(savedOrder);
        assertEquals(OrderStatus.PLACED, savedOrder.status());
        assertEquals("C-456", savedOrder.customerId().value());

        assertEquals(1, eventPublisher.publishCount());
        assertInstanceOf(OrderPlacedEvent.class, eventPublisher.publishedEvents().get(0));

        OrderPlacedEvent event = (OrderPlacedEvent) eventPublisher.publishedEvents().get(0);

        assertEquals(savedOrder.id(), event.orderId());
        assertEquals(savedOrder.customerId(), event.customerId());
        assertEquals(savedOrder.total(), event.total());
    }

    @Test
    void shouldNotSaveOrPublishWhenBusinessRuleFails() {
        // Given
        FakeOrderRepository orderRepository = new FakeOrderRepository();
        FakeDomainEventPublisher eventPublisher = new FakeDomainEventPublisher();
        FakeTransactionManager transactionManager = new FakeTransactionManager();

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
                new BigDecimal("99.00")
        );

        // When / Then
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.placeOrder(command)
        );

        assertEquals("Expected total does not match calculated total", exception.getMessage());
        assertEquals(0, orderRepository.saveCount());
        assertEquals(0, eventPublisher.publishCount());
    }
}