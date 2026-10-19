package com.example.urlshortener.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.urlshortener.admin.AdminAuthService;
import com.example.urlshortener.admin.AdminProperties;
import com.example.urlshortener.enterprise.EnterpriseApiKey;
import com.example.urlshortener.enterprise.EnterpriseApiKeyRepository;
import com.example.urlshortener.enterprise.EnterpriseApiKeyService;
import com.example.urlshortener.enterprise.EnterpriseProperties;
import com.example.urlshortener.service.RateLimitService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SecretRotationTest {

    @Test
    void acceptsPreviousAdminTokenDuringRotationWindow() {
        var properties = new AdminProperties("new-token");
        properties.setPreviousTokens(List.of("old-token"));
        assertThatCode(() -> new AdminAuthService(properties).requireAdmin("old-token")).doesNotThrowAnyException();
    }

    @Test
    void authenticatesAnApiKeyCreatedWithThePreviousHmacSecret() {
        var repository = mock(EnterpriseApiKeyRepository.class);
        var properties = new EnterpriseProperties();
        properties.setApiKeyHashSalt("new-secret");
        properties.setPreviousApiKeyHashSalts(List.of("old-secret"));
        String raw = "us_existing_key";
        String oldMarker = SecretSupport.hmacSha256("old-secret", raw);
        when(repository.findByKeyHash(oldMarker)).thenReturn(Optional.of(
                new EnterpriseApiKey("client", oldMarker, "ENTERPRISE", 100, null)));

        var service = new EnterpriseApiKeyService(repository, properties, mock(RateLimitService.class),
                Clock.fixed(Instant.parse("2026-09-14T00:00:00Z"), ZoneOffset.UTC));

        assertThatCode(() -> service.authenticate(raw)).doesNotThrowAnyException();
    }
}
