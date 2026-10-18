package pl.jakubtworek.booking.support;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import pl.jakubtworek.booking.entity.UserRole;
import pl.jakubtworek.booking.security.SecurityPrincipal;

import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

/** Tworzy kontrolowane tożsamości bez generowania JWT w testach kontraktu MVC. */
public final class SecurityTestActors {
    private SecurityTestActors() {
    }

    public static RequestPostProcessor eventManager(UUID organizationId) {
        return actor(UUID.randomUUID(), organizationId, "manager@example.test", UserRole.EVENT_MANAGER);
    }

    public static RequestPostProcessor customer(String email) {
        return actor(UUID.randomUUID(), null, email, UserRole.CUSTOMER);
    }

    public static RequestPostProcessor actor(UUID userId, UUID organizationId, String email, UserRole role) {
        var principal = new SecurityPrincipal(userId, organizationId, email, role);
        var token = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))
        );
        return authentication(token);
    }
}
