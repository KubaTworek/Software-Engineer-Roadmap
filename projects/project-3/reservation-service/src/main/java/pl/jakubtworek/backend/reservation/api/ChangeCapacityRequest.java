package pl.jakubtworek.backend.reservation.api;

import jakarta.validation.constraints.Min;

public record ChangeCapacityRequest(@Min(0) long expectedVersion, @Min(0) int newTotalTickets) { }
