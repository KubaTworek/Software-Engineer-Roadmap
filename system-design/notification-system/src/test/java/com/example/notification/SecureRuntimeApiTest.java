package com.example.notification;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"notification.security.jwt-secret=01234567890123456789012345678901",
        "notification.scheduling.enabled=false"})
@AutoConfigureMockMvc
@ActiveProfiles("secure-runtime")
class SecureRuntimeApiTest {
    @Autowired MockMvc mvc;

    @Test void unsignedRequestIsRejected() throws Exception {
        mvc.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized());
    }

    @Test void tenantComesFromSignedClaimRatherThanSpoofedHeader() throws Exception {
        mvc.perform(get("/api/v1/notifications").header("X-Tenant-Id", "attacker")
                        .with(jwt().jwt(token -> token.claim("tenant_id", "trusted-tenant"))))
                .andExpect(status().isOk());
    }

    @Test void adminControlPlaneRequiresAdminAuthority() throws Exception {
        var identity = jwt().jwt(token -> token.claim("tenant_id", "trusted-tenant"));
        mvc.perform(get("/api/v1/admin/ops/dashboard").with(identity)).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/ops/dashboard")
                        .with(jwt().jwt(token -> token.claim("tenant_id", "trusted-tenant"))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }
}
