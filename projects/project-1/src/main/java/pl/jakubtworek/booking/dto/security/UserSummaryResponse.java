package pl.jakubtworek.booking.dto.security;

import pl.jakubtworek.booking.entity.UserRole;
import java.util.UUID;

public record UserSummaryResponse(UUID id, UUID organizationId, String email, UserRole role) {
}
