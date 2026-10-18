package com.example.newsfeed.events;

import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.UUID;

/**
 * Idempotencja jest lokalna dla projekcji. Ten sam event ma zostać wykonany
 * raz przez fan-out, raz przez search i raz przez liczniki — nie raz globalnie.
 */
@Embeddable
public record ProcessedKafkaEventId(String consumerName, UUID eventId) implements Serializable { }
