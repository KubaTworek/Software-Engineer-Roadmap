package pl.jakubtworek.marketplace.integration.contracts;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import pl.jakubtworek.marketplace.ordering.domain.OrderPlaced;
import pl.jakubtworek.marketplace.shared.kernel.Money;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Anti-corruption layer between serialized integration contracts and the internal
 * domain event used by application handlers.
 *
 * The important rule is that application handlers should not care whether the
 * payload came from V1 or V2. They receive one normalized OrderPlaced event.
 */
public class OrderPlacedContractMapper {
    private final ObjectMapper objectMapper;

    public OrderPlacedContractMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy()
                .findAndRegisterModules()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public OrderPlaced toDomainEvent(String payload, int eventVersion) {
        try {
            return switch (eventVersion) {
                case 1 -> fromV1(objectMapper.readValue(payload, OrderPlacedV1.class));
                case 2 -> fromV2(objectMapper.readValue(payload, OrderPlacedV2.class));
                default -> throw new UnsupportedEventVersionException("Unsupported OrderPlaced event version: " + eventVersion);
            };
        } catch (UnsupportedEventVersionException e) {
            throw e;
        } catch (Exception e) {
            throw new UnsupportedEventVersionException("Cannot deserialize OrderPlaced v" + eventVersion, e);
        }
    }

    private OrderPlaced fromV1(OrderPlacedV1 event) {
        UUID aggregateId = firstNonNull(event.aggregateId(), event.orderId());
        if (aggregateId == null) {
            throw new UnsupportedEventVersionException("OrderPlaced v1 requires aggregateId or orderId");
        }
        if (event.totalAmount() == null || event.currency() == null) {
            throw new UnsupportedEventVersionException("OrderPlaced v1 requires totalAmount and currency");
        }

        List<OrderPlaced.Line> lines = event.lines() == null ? List.of() : event.lines().stream()
                .map(line -> new OrderPlaced.Line(
                        line.productId(),
                        line.quantity(),
                        Money.of(line.unitPriceAmount(), line.currency())
                ))
                .toList();

        return new OrderPlaced(
                firstNonNull(event.eventId(), UUID.randomUUID()),
                aggregateId,
                event.customerId(),
                Money.of(event.totalAmount(), event.currency()),
                lines,
                firstNonNull(event.occurredAt(), Instant.now()),
                firstNonNull(event.correlationId(), UUID.randomUUID()),
                event.causationId()
        );
    }

    private OrderPlaced fromV2(OrderPlacedV2 event) {
        UUID aggregateId = firstNonNull(event.aggregateId(), event.orderId());
        if (aggregateId == null) {
            throw new UnsupportedEventVersionException("OrderPlaced v2 requires aggregateId or orderId");
        }
        if (event.total() == null) {
            throw new UnsupportedEventVersionException("OrderPlaced v2 requires total");
        }

        List<OrderPlaced.Line> lines = event.lines() == null ? List.of() : event.lines().stream()
                .map(line -> new OrderPlaced.Line(
                        line.productId(),
                        line.quantity(),
                        Money.of(line.unitPrice().amount(), line.unitPrice().currency())
                ))
                .toList();

        return new OrderPlaced(
                firstNonNull(event.eventId(), UUID.randomUUID()),
                aggregateId,
                event.customerId(),
                Money.of(event.total().amount(), event.total().currency()),
                lines,
                firstNonNull(event.occurredAt(), Instant.now()),
                firstNonNull(event.correlationId(), UUID.randomUUID()),
                event.causationId()
        );
    }

    private static <T> T firstNonNull(T first, T second) {
        return first != null ? first : second;
    }
}
