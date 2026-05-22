package pl.jakubtworek.backend_engineering.stage_2.block_b.domain.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Base interface for all domain events published by the e-commerce system.
 *
 * A domain event represents something that has already happened in the business domain.
 * Event names should therefore be expressed in the past tense, for example:
 * OrderPlaced, PaymentAuthorized, PaymentFailed, ShippingInitiated.
 */
public interface DomainEvent {

    /**
     * Returns metadata shared by all events.
     *
     * Metadata is used for tracing, versioning, deduplication and observability.
     */
    EventMetadata metadata();

    /**
     * Returns the business identifier of the aggregate related to this event.
     *
     * For order-related workflows, this will usually be the orderId.
     */
    String aggregateId();

    /**
     * Returns the logical event type.
     *
     * This value can be used for routing, logging or schema lookup.
     */
    String eventType();
}