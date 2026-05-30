package pl.jakubtworek.booking.service.security;

import org.springframework.security.core.Authentication;
import pl.jakubtworek.booking.security.SecurityPrincipal;

public final class AuthenticationAccess {
    private AuthenticationAccess() {
    }

    public static SecurityPrincipal principal(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof SecurityPrincipal principal)) {
            return null;
        }
        return principal;
    }
}
