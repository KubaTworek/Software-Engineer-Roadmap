package pl.jakubtworek.chatsystem.message;

import java.time.Instant;
import java.util.List;

public record MessagePageResponse(
        List<MessageResponse> items,
        Instant nextBefore,
        boolean hasMore
) {}
