package pl.jakubtworek.backend_engineering.stage_2.block_a.test.integration;

import org.junit.jupiter.api.Test;
import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.adapter.in.web.PlaceOrderHttpAdapter;
import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.adapter.in.web.PlaceOrderHttpRequest;
import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.adapter.in.web.PlaceOrderLineHttpRequest;
import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.application.port.OrderRepository;
import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.application.port.TransactionManager;
import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.application.service.PlaceOrderApplicationService;
import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.domain.model.Order;
import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.domain.model.OrderId;
import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.domain.model.OrderStatus;
import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.infrastructure.outbox.OutboxEventPublisher;
import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.infrastructure.outbox.OutboxMessage;
import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.infrastructure.outbox.OutboxMessageRepository;
import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.integration.event.OrderPlacedIntegrationEvent;
import pl.jakubtworek.backend_engineering.stage_2.block_a.use_case.integration.event.SalesIntegrationEventMapper;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlaceOrderBoundaryFlowTest {

    @Test
    void crossesHttpApplicationDomainAndOutboxBoundariesInOneTransaction() {
        TransactionalStore store = new TransactionalStore();
        OutboxEventPublisher outbox = new OutboxEventPublisher(
                store,
                new SalesIntegrationEventMapper(),
                event -> {
                    OrderPlacedIntegrationEvent placed = (OrderPlacedIntegrationEvent) event;
                    return placed.orderId() + "|" + placed.customerId() + "|"
                            + placed.total() + "|" + placed.currency() + "|v" + placed.schemaVersion();
                }
        );
        PlaceOrderHttpAdapter http = new PlaceOrderHttpAdapter(
                new PlaceOrderApplicationService(store, outbox, store)
        );

        String orderId = http.placeOrder(validRequest()).orderId();

        Order storedOrder = store.findById(OrderId.of(orderId)).orElseThrow();
        assertThat(storedOrder.status()).isEqualTo(OrderStatus.PLACED);
        assertThat(storedOrder.total().amount()).isEqualByComparingTo("100.00");
        assertThat(store.messages).singleElement().satisfies(message -> {
            assertThat(message.aggregateId()).isEqualTo(orderId);
            assertThat(message.eventType()).isEqualTo("sales.order-placed.v1");
            assertThat(message.payload()).isEqualTo(orderId + "|C-456|100.00|PLN|v1");
        });
        assertThat(store.commits).isEqualTo(1);
        assertThat(store.rollbacks).isZero();
    }

    @Test
    void rollsBackAggregateWhenCreatingTheOutboxRecordFails() {
        TransactionalStore store = new TransactionalStore();
        OutboxEventPublisher failingOutbox = new OutboxEventPublisher(
                store,
                new SalesIntegrationEventMapper(),
                event -> {
                    throw new IllegalStateException("serialization unavailable");
                }
        );
        PlaceOrderHttpAdapter http = new PlaceOrderHttpAdapter(
                new PlaceOrderApplicationService(store, failingOutbox, store)
        );

        assertThatThrownBy(() -> http.placeOrder(validRequest()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("serialization unavailable");

        assertThat(store.orders).isEmpty();
        assertThat(store.messages).isEmpty();
        assertThat(store.commits).isZero();
        assertThat(store.rollbacks).isEqualTo(1);
    }

    private static PlaceOrderHttpRequest validRequest() {
        return new PlaceOrderHttpRequest(
                "C-456",
                "PLN",
                List.of(new PlaceOrderLineHttpRequest(
                        "P-1",
                        2,
                        new BigDecimal("50.00")
                )),
                new BigDecimal("100.00")
        );
    }

    /**
     * Small unit-of-work fake: writes become visible only after the transaction callback succeeds.
     * It proves the use case boundary without pretending to replace a database integration test.
     */
    private static final class TransactionalStore
            implements OrderRepository, OutboxMessageRepository, TransactionManager {

        private final Map<String, Order> orders = new LinkedHashMap<>();
        private final List<OutboxMessage> messages = new ArrayList<>();
        private Map<String, Order> pendingOrders;
        private List<OutboxMessage> pendingMessages;
        private boolean transactionActive;
        private int commits;
        private int rollbacks;

        @Override
        public void executeInTransaction(Runnable action) {
            if (transactionActive) {
                throw new IllegalStateException("nested transactions are not supported by this fake");
            }
            transactionActive = true;
            pendingOrders = new LinkedHashMap<>();
            pendingMessages = new ArrayList<>();
            try {
                action.run();
                orders.putAll(pendingOrders);
                messages.addAll(pendingMessages);
                commits++;
            } catch (RuntimeException exception) {
                rollbacks++;
                throw exception;
            } finally {
                transactionActive = false;
                pendingOrders = null;
                pendingMessages = null;
            }
        }

        @Override
        public Optional<Order> findById(OrderId id) {
            return Optional.ofNullable(orders.get(id.value()));
        }

        @Override
        public void save(Order order) {
            requireTransaction();
            pendingOrders.put(order.id().value(), order);
        }

        @Override
        public void save(OutboxMessage message) {
            requireTransaction();
            pendingMessages.add(message);
        }

        @Override
        public List<OutboxMessage> findUnpublished(int limit) {
            return messages.stream()
                    .filter(message -> !message.published())
                    .limit(limit)
                    .toList();
        }

        private void requireTransaction() {
            if (!transactionActive) {
                throw new IllegalStateException("write attempted outside the use-case transaction");
            }
        }
    }
}
