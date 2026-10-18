package com.example.urlshortener.enterprise;

import com.example.urlshortener.exception.AdminUnauthorizedException;
import com.example.urlshortener.security.SecretSupport;
import com.example.urlshortener.service.RateLimitService;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Stores only keyed markers; the 256-bit raw API key is returned once at creation. */
@Service
public class EnterpriseApiKeyService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final EnterpriseApiKeyRepository repository;
    private final EnterpriseProperties properties;
    private final RateLimitService limits;
    private final Clock clock;

    public EnterpriseApiKeyService(EnterpriseApiKeyRepository repository, EnterpriseProperties properties,
            RateLimitService limits, Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.limits = limits;
        this.clock = clock;
    }

    @Transactional
    public CreateEnterpriseApiKeyResponse create(CreateEnterpriseApiKeyRequest request) {
        String raw = generateApiKey();
        String tier = request.tier() == null || request.tier().isBlank()
                ? "ENTERPRISE" : request.tier().trim().toUpperCase(Locale.ROOT);
        int limit = request.rateLimitPerMinute() == null
                ? properties.getDefaultRateLimitPerMinute() : request.rateLimitPerMinute();
        EnterpriseApiKey saved = repository.save(new EnterpriseApiKey(
                request.name(), hash(properties.getApiKeyHashSalt(), raw), tier, limit, request.expiresAt()));
        return new CreateEnterpriseApiKeyResponse(saved.getId(), saved.getName(), raw, saved.getTier(),
                saved.getRateLimitPerMinute(), saved.getExpiresAt(), saved.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public EnterprisePrincipal authenticate(String raw) {
        if (!properties.isEnabled()) throw new AdminUnauthorizedException("Enterprise API is disabled");
        if (raw == null || raw.isBlank()) throw new AdminUnauthorizedException("Enterprise API key is missing");

        EnterpriseApiKey key = hashCandidates(raw).stream()
                .map(repository::findByKeyHash)
                .flatMap(java.util.Optional::stream)
                .findFirst()
                .orElseThrow(() -> new AdminUnauthorizedException("Enterprise API key is invalid"));
        if (!key.isActive(Instant.now(clock))) {
            throw new AdminUnauthorizedException("Enterprise API key is inactive or expired");
        }
        limits.checkFixedWindow("rl:enterprise:" + key.getId(), key.getRateLimitPerMinute(), Duration.ofMinutes(1));
        return new EnterprisePrincipal(key.getId(), key.getName(), key.getTier(), key.getRateLimitPerMinute());
    }

    private String generateApiKey() {
        byte[] entropy = new byte[32];
        RANDOM.nextBytes(entropy);
        return "us_" + HexFormat.of().formatHex(entropy);
    }

    private java.util.List<String> hashCandidates(String raw) {
        var hashes = new ArrayList<String>();
        hashes.add(hash(properties.getApiKeyHashSalt(), raw));
        properties.getPreviousApiKeyHashSalts().forEach(secret -> hashes.add(hash(secret, raw)));
        return hashes;
    }

    private String hash(String secret, String raw) {
        return SecretSupport.hmacSha256(secret, raw);
    }
}
