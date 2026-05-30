package pl.jakubtworek.booking.security;

import pl.jakubtworek.booking.entity.UserRole;

import java.util.UUID;

public record SecurityPrincipal(UUID userId, UUID organizationId, String email, UserRole role) {
}
