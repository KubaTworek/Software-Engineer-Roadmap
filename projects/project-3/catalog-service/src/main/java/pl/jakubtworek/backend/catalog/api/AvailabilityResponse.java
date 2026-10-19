package pl.jakubtworek.backend.catalog.api;

import java.io.Serializable;
import java.util.UUID;

public record AvailabilityResponse(UUID eventId, int totalTickets, int availableTickets) implements Serializable {
}
