package pl.jakubtworek.booking.service.security;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component("tenantSecurity")
public class TenantSecurity {
    public boolean sameOrganization(Authentication authentication, UUID organizationId) {
        var principal = AuthenticationAccess.principal(authentication);
        return principal != null && principal.organizationId() != null && principal.organizationId().equals(organizationId);
    }
}
