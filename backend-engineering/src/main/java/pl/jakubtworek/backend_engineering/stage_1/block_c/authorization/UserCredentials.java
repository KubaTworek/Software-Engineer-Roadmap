package pl.jakubtworek.backend_engineering.stage_1.block_c.authorization;

import java.util.List;

public record UserCredentials(
        String username,
        String passwordHash,
        boolean enabled,
        List<String> roles,
        List<String> permissions
) {
    public UserCredentials {
        roles = List.copyOf(roles);
        permissions = List.copyOf(permissions);
    }
}
