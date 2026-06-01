package pl.jakubtworek.marketplace.integration.contracts;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Current OrderPlaced integration contract.
 *
 * V2 uses a structured Money object and adds salesChannel as a backward-compatible
 * optional field. Consumers must ignore fields they do not need.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderPlacedV2(
        UUID eventId,
        UUID aggregateId,
        UUID orderId,
        UUID customerId,
        MoneyDto total,
        List<Line> lines,
        String salesChannel,
        Instant occurredAt,
        UUID correlationId,
        UUID causationId
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MoneyDto(String amount, String currency) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Line(UUID productId, int quantity, MoneyDto unitPrice) {}
}
