package pl.jakubtworek.backend.reservation.api;

import java.util.UUID;

public record InventoryCapacityResponse(UUID eventId, int totalTickets, int availableTickets, long version) { }
