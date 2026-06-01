package pl.jakubtworek.marketplace.integration.contracts;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Historical integration contract.
 *
 * V1 intentionally keeps the money representation flat. It may come from an older
 * producer and must still be accepted by consumers that only need data available
 * in this version, for example payment reservation.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderPlacedV1(
        UUID eventId,
        UUID aggregateId,
        UUID orderId,
        UUID customerId,
        String totalAmount,
        String currency,
        List<Line> lines,
        Instant occurredAt,
        UUID correlationId,
        UUID causationId
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Line(UUID productId, int quantity, String unitPriceAmount, String currency) {}
}
