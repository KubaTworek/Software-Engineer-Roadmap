package pl.jakubtworek.backend_engineering.stage_2.block_a.api_design;

import java.util.List;

public record OrderPage(List<OrderResource> items, String nextCursor) {

    public OrderPage {
        items = List.copyOf(items);
    }
}
