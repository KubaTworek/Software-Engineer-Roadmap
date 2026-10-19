package pl.jakubtworek.backend_engineering.stage_2.block_a.api_design;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * At-least-once webhook: każda próba zachowuje delivery id, timestamp, payload
 * i podpis. Odbiorca deduplikuje po delivery id.
 */
public final class WebhookDeliveryService {

    private final Clock clock;
    private final Supplier<UUID> idSupplier;
    private final HmacWebhookSigner signer;
    private final WebhookTransport transport;
    private final int maxAttempts;
    private final Map<UUID, Delivery> deliveries = new HashMap<>();

    public WebhookDeliveryService(
            Clock clock,
            Supplier<UUID> idSupplier,
            HmacWebhookSigner signer,
            WebhookTransport transport,
            int maxAttempts
    ) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
        this.clock = Objects.requireNonNull(clock);
        this.idSupplier = Objects.requireNonNull(idSupplier);
        this.signer = Objects.requireNonNull(signer);
        this.transport = Objects.requireNonNull(transport);
        this.maxAttempts = maxAttempts;
    }

    public synchronized Delivery enqueue(String eventType, String payload) {
        UUID id = idSupplier.get();
        long timestamp = clock.instant().getEpochSecond();
        Delivery delivery = new Delivery(
                id, eventType, payload, timestamp, signer.sign(timestamp, payload),
                0, State.PENDING, clock.instant(), null);
        deliveries.put(id, delivery);
        return delivery;
    }

    public synchronized Delivery attempt(UUID deliveryId) {
        Delivery current = get(deliveryId);
        if (current.state() == State.DELIVERED || current.state() == State.EXHAUSTED) {
            return current;
        }
        if (clock.instant().isBefore(current.nextAttemptAt())) {
            return current;
        }

        int attemptNumber = current.attempts() + 1;
        boolean accepted;
        try {
            accepted = transport.send(new WebhookRequest(
                    current.id(), current.eventType(), current.payload(),
                    current.timestampEpochSecond(), current.signature()));
        } catch (RuntimeException transportFailure) {
            // Błąd sieci jest nieudaną próbą, nie powodem utraty stanu delivery.
            accepted = false;
        }
        State state = accepted ? State.DELIVERED
                : attemptNumber >= maxAttempts ? State.EXHAUSTED : State.PENDING;
        Instant next = state == State.PENDING
                ? clock.instant().plus(backoff(attemptNumber))
                : current.nextAttemptAt();
        Delivery updated = new Delivery(
                current.id(), current.eventType(), current.payload(), current.timestampEpochSecond(),
                current.signature(), attemptNumber, state, next, accepted ? clock.instant() : null);
        deliveries.put(updated.id(), updated);
        return updated;
    }

    public synchronized Delivery get(UUID deliveryId) {
        Delivery delivery = deliveries.get(deliveryId);
        if (delivery == null) {
            throw ApiFailure.notFound("Webhook delivery " + deliveryId);
        }
        return delivery;
    }

    private static Duration backoff(int failedAttempt) {
        return Duration.ofSeconds(1L << Math.min(failedAttempt - 1, 10));
    }

    public enum State {
        PENDING,
        DELIVERED,
        EXHAUSTED
    }

    public record Delivery(
            UUID id,
            String eventType,
            String payload,
            long timestampEpochSecond,
            String signature,
            int attempts,
            State state,
            Instant nextAttemptAt,
            Instant deliveredAt
    ) {
    }

    public record WebhookRequest(
            UUID deliveryId,
            String eventType,
            String payload,
            long timestampEpochSecond,
            String signature
    ) {
    }

    @FunctionalInterface
    public interface WebhookTransport {
        boolean send(WebhookRequest request);
    }
}
