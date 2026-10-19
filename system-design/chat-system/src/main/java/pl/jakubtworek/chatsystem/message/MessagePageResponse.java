package pl.jakubtworek.chatsystem.message;

import java.util.List;

public record MessagePageResponse(
        List<MessageResponse> items,
        Long nextBeforeSequence,
        boolean hasMore
) {}
