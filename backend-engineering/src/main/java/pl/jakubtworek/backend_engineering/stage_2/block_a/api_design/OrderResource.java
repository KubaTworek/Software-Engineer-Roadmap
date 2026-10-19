package pl.jakubtworek.backend_engineering.stage_2.block_a.api_design;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResource(
        UUID id,
        String customerEmail,
        List<LineItem> items,
        boolean expedited,
        Status status,
        long version,
        Instant createdAt
) {

    public OrderResource {
        items = List.copyOf(items);
    }

    public enum Status {
        NEW,
        CANCELLED
    }

    public record LineItem(String sku, int quantity) {
    }
}
