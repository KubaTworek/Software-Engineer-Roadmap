package pl.jakubtworek.backend_engineering.stage_1.block_c.jpa;

import java.util.List;

public record UserCursorPage(List<UserListItem> items, UserCursor nextCursor) {

    public UserCursorPage {
        items = List.copyOf(items);
    }
}
