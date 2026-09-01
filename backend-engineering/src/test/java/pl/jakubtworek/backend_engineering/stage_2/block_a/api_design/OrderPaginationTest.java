package pl.jakubtworek.backend_engineering.stage_2.block_a.api_design;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderPaginationTest {

    @Test
    void cursorContinuesAfterTheLastStableSortKeyAndCanFilterByStatus() {
        Queue<UUID> ids = new ArrayDeque<>(List.of(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                UUID.fromString("00000000-0000-0000-0000-000000000003")));
        OrderService service = new OrderService(
                Clock.fixed(Instant.parse("2026-01-01T00:00:00.123456789Z"), ZoneOffset.UTC), ids::remove);
        create(service, "one");
        OrderResource cancelled = create(service, "two");
        create(service, "three");
        service.cancel(cancelled.id());

        OrderPage first = service.list(2, null, null, OrderService.Sort.CREATED_ASC);
        OrderPage second = service.list(2, first.nextCursor(), null, OrderService.Sort.CREATED_ASC);

        assertThat(first.items()).extracting(OrderResource::id).containsExactly(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                UUID.fromString("00000000-0000-0000-0000-000000000002"));
        assertThat(second.items()).extracting(OrderResource::id)
                .containsExactly(UUID.fromString("00000000-0000-0000-0000-000000000003"));
        assertThat(second.nextCursor()).isNull();
        assertThat(service.list(20, null, OrderResource.Status.CANCELLED, OrderService.Sort.CREATED_ASC).items())
                .extracting(OrderResource::id).containsExactly(cancelled.id());
    }

    @Test
    void malformedCursorAndUnsupportedSortAreClientErrors() {
        OrderService service = new OrderService(Clock.systemUTC());

        assertThatThrownBy(() -> service.list(10, "not-base64", null, OrderService.Sort.CREATED_ASC))
                .isInstanceOf(ApiFailure.class)
                .extracting("code").isEqualTo("invalid_request");
        assertThatThrownBy(() -> OrderService.Sort.parse("email"))
                .isInstanceOf(ApiFailure.class)
                .extracting("code").isEqualTo("invalid_request");
    }

    private static OrderResource create(OrderService service, String key) {
        return service.create(key, new OrderCommands.Create(
                key + "@example.com", List.of(new OrderResource.LineItem("SKU-" + key, 1)), false)).order();
    }
}
