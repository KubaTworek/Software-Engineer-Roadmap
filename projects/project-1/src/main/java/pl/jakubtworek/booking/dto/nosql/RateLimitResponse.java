package pl.jakubtworek.booking.dto.nosql;

import java.time.Instant;

public record RateLimitResponse(
        String clientKey,
        boolean allowed,
        int remainingTokens,
        Instant resetAt
) {
}
