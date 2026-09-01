package pl.jakubtworek.cloudarchitecture.service;

import org.junit.jupiter.api.Test;
import pl.jakubtworek.cloudarchitecture.entity.ProcessedOrderEventEntity;
import pl.jakubtworek.cloudarchitecture.repository.ProcessedOrderEventRepository;
import pl.jakubtworek.cloudarchitecture.service.OrderFulfillmentGateway;
import pl.jakubtworek.cloudarchitecture.service.OrderWorker;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderWorkerTest {

    private static final Instant NOW = Instant.parse("2026-08-30T12:00:00Z");

    @Test
    void skipsAnAlreadyProcessedRedelivery() {
        ProcessedOrderEventRepository processedEvents = mock(ProcessedOrderEventRepository.class);
        OrderFulfillmentGateway gateway = mock(OrderFulfillmentGateway.class);
        when(processedEvents.existsById(42L)).thenReturn(true);

        worker(processedEvents, gateway).processOrderCreated(42L);

        verify(gateway, never()).fulfill(42L, "order-created:42");
        verify(processedEvents, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void passesDeterministicIdempotencyKeyAndPersistsTheMarker() {
        ProcessedOrderEventRepository processedEvents = mock(ProcessedOrderEventRepository.class);
        OrderFulfillmentGateway gateway = mock(OrderFulfillmentGateway.class);
        when(processedEvents.existsById(42L)).thenReturn(false);

        worker(processedEvents, gateway).processOrderCreated(42L);

        verify(gateway).fulfill(42L, "order-created:42");
        verify(processedEvents).save(argThat(marker ->
                marker.getOrderId().equals(42L) && marker.getProcessedAt().equals(NOW)
        ));
    }

    @Test
    void doesNotMarkFailedDownstreamWorkAsProcessed() {
        ProcessedOrderEventRepository processedEvents = mock(ProcessedOrderEventRepository.class);
        OrderFulfillmentGateway gateway = mock(OrderFulfillmentGateway.class);
        RuntimeException failure = new RuntimeException("invoice service unavailable");
        doThrow(failure).when(gateway).fulfill(42L, "order-created:42");

        assertThatThrownBy(() -> worker(processedEvents, gateway).processOrderCreated(42L))
                .isSameAs(failure);
        verify(processedEvents, never()).save(org.mockito.ArgumentMatchers.any(ProcessedOrderEventEntity.class));
    }

    private static OrderWorker worker(
            ProcessedOrderEventRepository processedEvents,
            OrderFulfillmentGateway gateway
    ) {
        return new OrderWorker(processedEvents, gateway, Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
