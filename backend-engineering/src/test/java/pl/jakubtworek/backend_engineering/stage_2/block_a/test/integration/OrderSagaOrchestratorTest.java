package pl.jakubtworek.backend_engineering.stage_2.block_a.test.integration;

import org.junit.jupiter.api.Test;
import pl.jakubtworek.backend_engineering.stage_2.block_a.integration.sales.saga.OrderSaga;
import pl.jakubtworek.backend_engineering.stage_2.block_a.integration.sales.saga.OrderSagaOrchestrator;
import pl.jakubtworek.backend_engineering.stage_2.block_a.integration.sales.saga.OrderSagaRepository;
import pl.jakubtworek.backend_engineering.stage_2.block_a.integration.sales.saga.OrderSagaState;
import pl.jakubtworek.backend_engineering.stage_2.block_a.integration.sales.saga.SagaCommandBus;
import pl.jakubtworek.backend_engineering.stage_2.block_a.integration.shared.event.InventoryReservedEvent;
import pl.jakubtworek.backend_engineering.stage_2.block_a.integration.shared.event.OrderPlacedEvent;
import pl.jakubtworek.backend_engineering.stage_2.block_a.integration.shared.event.PaymentCompletedEvent;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderSagaOrchestratorTest {

    @Test
    void shouldIgnoreDuplicateEventsAndSendEveryCommandOnce() {
        InMemorySagaRepository repository = new InMemorySagaRepository();
        RecordingCommandBus commandBus = new RecordingCommandBus();
        OrderSagaOrchestrator orchestrator = new OrderSagaOrchestrator(repository, commandBus);
        OrderPlacedEvent placed = new OrderPlacedEvent(
                "E-1", "O-1", "C-1", List.of(), BigDecimal.TEN, "PLN", Instant.now()
        );
        PaymentCompletedEvent paid = new PaymentCompletedEvent(
                "E-2", "O-1", "P-1", BigDecimal.TEN, "PLN", Instant.now()
        );
        InventoryReservedEvent reserved = new InventoryReservedEvent(
                "E-3", "O-1", "R-1", Instant.now()
        );

        orchestrator.on(placed);
        orchestrator.on(placed);
        orchestrator.on(paid);
        orchestrator.on(paid);
        orchestrator.on(reserved);
        orchestrator.on(reserved);

        assertEquals(
                List.of("authorize:O-1", "reserve:O-1", "ship:O-1"),
                commandBus.commands
        );
        assertEquals(OrderSagaState.COMPLETED, repository.findByOrderId("O-1").orElseThrow().state());
    }

    private static final class InMemorySagaRepository implements OrderSagaRepository {

        private final Map<String, OrderSaga> sagas = new HashMap<>();

        @Override
        public Optional<OrderSaga> findByOrderId(String orderId) {
            return Optional.ofNullable(sagas.get(orderId));
        }

        @Override
        public void save(OrderSaga saga) {
            sagas.put(saga.orderId(), saga);
        }
    }

    private static final class RecordingCommandBus implements SagaCommandBus {

        private final List<String> commands = new ArrayList<>();

        @Override public void sendAuthorizePayment(String orderId) { commands.add("authorize:" + orderId); }
        @Override public void sendReserveInventory(String orderId) { commands.add("reserve:" + orderId); }
        @Override public void sendScheduleShipment(String orderId) { commands.add("ship:" + orderId); }
        @Override public void sendCancelPayment(String orderId) { commands.add("cancel-payment:" + orderId); }
        @Override public void sendReleaseInventory(String orderId) { commands.add("release:" + orderId); }
    }
}
