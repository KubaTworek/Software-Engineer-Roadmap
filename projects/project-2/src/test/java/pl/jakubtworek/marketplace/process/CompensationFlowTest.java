package pl.jakubtworek.marketplace.process;

import org.junit.jupiter.api.Test;
import pl.jakubtworek.marketplace.integration.contracts.PaymentReleaseRequestedIntegrationEvent;
import pl.jakubtworek.marketplace.integration.contracts.StockReleaseRequestedIntegrationEvent;
import pl.jakubtworek.marketplace.integration.outbox.OutboxEventMapper;
import pl.jakubtworek.marketplace.inventory.application.ReleaseStockHandler;
import pl.jakubtworek.marketplace.inventory.application.StockReservationRepository;
import pl.jakubtworek.marketplace.inventory.domain.StockItem;
import pl.jakubtworek.marketplace.inventory.infrastructure.InMemoryStockRepository;
import pl.jakubtworek.marketplace.inventory.infrastructure.InMemoryStockReservationRepository;
import pl.jakubtworek.marketplace.payment.application.ReleasePaymentHandler;
import pl.jakubtworek.marketplace.payment.domain.Payment;
import pl.jakubtworek.marketplace.payment.domain.PaymentStatus;
import pl.jakubtworek.marketplace.payment.infrastructure.InMemoryPaymentRepository;
import pl.jakubtworek.marketplace.testsupport.RecordingEventPublisher;
import tools.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class CompensationFlowTest {
    private final OutboxEventMapper mapper = new OutboxEventMapper(new ObjectMapper());

    @Test void stockFailurePublishesCommandAndActuallyReleasesPaymentOnlyOnce() {
        UUID orderId = UUID.randomUUID();
        var payments = new InMemoryPaymentRepository();
        payments.save(Payment.reserve(orderId, pl.jakubtworek.marketplace.shared.kernel.Money.of("10", "PLN"),
                true, UUID.randomUUID(), UUID.randomUUID()));
        var published = new RecordingEventPublisher();
        var manager = new OrderFulfillmentProcessManager(new InMemoryOrderFulfillmentProcessRepository(), Duration.ofMinutes(5), published);

        manager.paymentReserved(orderId);
        manager.stockRejected(orderId);

        var command = published.eventsOfType(PaymentReleaseRequested.class).getFirst();
        var contract = (PaymentReleaseRequestedIntegrationEvent) mapper.toDomainEvent(mapper.toOutboxEvent(command));
        var handler = new ReleasePaymentHandler(payments);
        handler.handle(contract);
        handler.handle(contract); // broker redelivery
        assertThat(payments.findByOrderId(orderId).orElseThrow().status()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test void paymentFailureReleasesRecordedStockAndDuplicateDoesNotInflateAvailability() {
        UUID orderId = UUID.randomUUID(); UUID productId = UUID.randomUUID();
        var stock = new InMemoryStockRepository();
        var item = StockItem.create(productId, 10); item.reserveWithoutPublishingEvent(3); stock.save(item);
        var reservations = new InMemoryStockReservationRepository();
        reservations.record(orderId, List.of(new StockReservationRepository.Line(productId, 3)));
        var published = new RecordingEventPublisher();
        var manager = new OrderFulfillmentProcessManager(new InMemoryOrderFulfillmentProcessRepository(), Duration.ofMinutes(5), published);
        manager.stockReserved(orderId);
        manager.paymentRejected(orderId);

        var command = published.eventsOfType(StockReleaseRequested.class).getFirst();
        var contract = (StockReleaseRequestedIntegrationEvent) mapper.toDomainEvent(mapper.toOutboxEvent(command));
        var handler = new ReleaseStockHandler(stock, reservations);
        handler.handle(contract); handler.handle(contract);
        assertThat(stock.findByProductId(productId).orElseThrow().availableQuantity()).isEqualTo(10);
        assertThat(stock.findByProductId(productId).orElseThrow().reservedQuantity()).isZero();
    }
}
