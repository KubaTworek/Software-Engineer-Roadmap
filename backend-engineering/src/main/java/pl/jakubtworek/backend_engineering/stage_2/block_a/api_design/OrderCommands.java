package pl.jakubtworek.backend_engineering.stage_2.block_a.api_design;

import java.util.List;

final class OrderCommands {

    private OrderCommands() {
    }

    record Create(String customerEmail, List<OrderResource.LineItem> items, boolean expedited) {
        Create {
            items = List.copyOf(items);
        }

        String fingerprint() {
            return customerEmail + '|' + expedited + '|' + items;
        }
    }

    record Replace(String customerEmail, List<OrderResource.LineItem> items, boolean expedited) {
        Replace {
            items = List.copyOf(items);
        }
    }

    record Patch(String customerEmail, Boolean expedited) {
        boolean isEmpty() {
            return customerEmail == null && expedited == null;
        }
    }
}
