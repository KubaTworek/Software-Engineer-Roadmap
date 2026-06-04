package pl.jakubtworek.backend.catalog.api;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

public record EventResponse(UUID id, String name, String venue, Instant startsAt, int totalTickets, int availableTickets) implements Serializable {
}
