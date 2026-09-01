package pl.jakubtworek.backend_engineering.stage_2.block_a.api_design;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AsyncCancellationTest {

    @Test
    void acceptedDoesNotMeanCompletedAndCompletionIsIdempotent() {
        Clock clock = Clock.fixed(Instant.parse("2026-02-01T12:00:00Z"), ZoneOffset.UTC);
        UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000000011");
        UUID operationId = UUID.fromString("00000000-0000-0000-0000-000000000012");
        OrderService orders = new OrderService(clock, () -> orderId);
        orders.create("create-11", new OrderCommands.Create(
                "buyer@example.com", List.of(new OrderResource.LineItem("SKU-1", 1)), false));
        List<OrderResource> published = new ArrayList<>();
        AsyncCancellationService service = new AsyncCancellationService(
                orders, clock, () -> operationId, published::add);

        AsyncCancellationService.Operation accepted = service.start(orderId);

        assertThat(accepted.state()).isEqualTo(AsyncCancellationService.State.PENDING);
        assertThat(orders.get(orderId).status()).isEqualTo(OrderResource.Status.NEW);

        AsyncCancellationService.Operation completed = service.complete(operationId);
        AsyncCancellationService.Operation replayed = service.complete(operationId);

        assertThat(completed.state()).isEqualTo(AsyncCancellationService.State.SUCCEEDED);
        assertThat(replayed).isEqualTo(completed);
        assertThat(orders.get(orderId).status()).isEqualTo(OrderResource.Status.CANCELLED);
        assertThat(published).hasSize(1);
    }
}
