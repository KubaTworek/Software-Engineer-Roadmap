package pl.jakubtworek.booking.service.security;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import pl.jakubtworek.booking.entity.UserRole;

import java.util.UUID;

@Component("organizationUserSecurity")
public class OrganizationUserSecurity {
    public boolean canManage(Authentication authentication, UUID organizationId) {
        var principal = AuthenticationAccess.principal(authentication);
        return principal != null
                && principal.role() == UserRole.ORG_ADMIN
                && principal.organizationId() != null
                && principal.organizationId().equals(organizationId);
    }
}
