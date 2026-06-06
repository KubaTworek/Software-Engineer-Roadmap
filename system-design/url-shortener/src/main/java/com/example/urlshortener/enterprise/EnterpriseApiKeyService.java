package com.example.urlshortener.enterprise;

import com.example.urlshortener.exception.AdminUnauthorizedException;
import com.example.urlshortener.service.RateLimitService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EnterpriseApiKeyService {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final EnterpriseApiKeyRepository repository;
    private final EnterpriseProperties properties;
    private final RateLimitService rateLimitService;
    private final Clock clock;

    public EnterpriseApiKeyService(
        EnterpriseApiKeyRepository repository,
        EnterpriseProperties properties,
        RateLimitService rateLimitService,
        Clock clock
    ) {
        this.repository = repository;
        this.properties = properties;
        this.rateLimitService = rateLimitService;
        this.clock = clock;
    }

    @Transactional
    public CreateEnterpriseApiKeyResponse create(CreateEnterpriseApiKeyRequest request) {
        String rawKey = generateApiKey();
        String hash = hash(rawKey);
        String tier = request.tier() == null || request.tier().isBlank() ? "ENTERPRISE" : request.tier().trim().toUpperCase();
        int rateLimit = request.rateLimitPerMinute() == null ? properties.getDefaultRateLimitPerMinute() : request.rateLimitPerMinute();

        EnterpriseApiKey saved = repository.save(new EnterpriseApiKey(request.name(), hash, tier, rateLimit, request.expiresAt()));
        return new CreateEnterpriseApiKeyResponse(
            saved.getId(), saved.getName(), rawKey, saved.getTier(), saved.getRateLimitPerMinute(), saved.getExpiresAt(), saved.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public EnterprisePrincipal authenticate(String rawKey) {
        if (!properties.isEnabled()) {
            throw new AdminUnauthorizedException("Enterprise API is disabled");
        }
        if (rawKey == null || rawKey.isBlank()) {
            throw new AdminUnauthorizedException("Enterprise API key is missing");
        }

        EnterpriseApiKey key = repository.findByKeyHash(hash(rawKey))
            .orElseThrow(() -> new AdminUnauthorizedException("Enterprise API key is invalid"));
        if (!key.isActive(Instant.now(clock))) {
            throw new AdminUnauthorizedException("Enterprise API key is inactive or expired");
        }

        rateLimitService.checkFixedWindow(
            "rl:enterprise:" + key.getId(),
            key.getRateLimitPerMinute(),
            Duration.ofMinutes(1)
        );

        return new EnterprisePrincipal(key.getId(), key.getName(), key.getTier(), key.getRateLimitPerMinute());
    }

    private String generateApiKey() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return "us_" + HexFormat.of().formatHex(bytes);
    }

    private String hash(String rawKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest((properties.getApiKeyHashSalt() + ":" + rawKey).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to hash API key", exception);
        }
    }
}
