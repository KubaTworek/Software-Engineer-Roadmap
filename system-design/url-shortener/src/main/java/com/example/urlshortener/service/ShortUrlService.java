package com.example.urlshortener.service;

import com.example.urlshortener.dto.CreateShortUrlRequest;
import com.example.urlshortener.dto.CreateShortUrlResponse;
import com.example.urlshortener.dto.UrlDetailsResponse;
import com.example.urlshortener.exception.CustomAliasAlreadyExistsException;
import com.example.urlshortener.exception.ShortUrlGoneException;
import com.example.urlshortener.exception.ShortUrlNotFoundException;
import com.example.urlshortener.model.ShortUrl;
import com.example.urlshortener.model.UrlStatus;
import com.example.urlshortener.repository.ShortUrlRepository;
import com.example.urlshortener.validation.AliasValidator;
import com.example.urlshortener.validation.UrlValidator;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

@Service
public class ShortUrlService {

    private final ShortUrlRepository repository;
    private final Base62Encoder base62Encoder;
    private final UrlValidator urlValidator;
    private final AliasValidator aliasValidator;
    private final ShortUrlCacheService cacheService;
    private final Clock clock;
    private final String publicBaseUrl;

    public ShortUrlService(
        ShortUrlRepository repository,
        Base62Encoder base62Encoder,
        UrlValidator urlValidator,
        AliasValidator aliasValidator,
        ShortUrlCacheService cacheService,
        Clock clock,
        @Value("${app.public-base-url}") String publicBaseUrl
    ) {
        this.repository = repository;
        this.base62Encoder = base62Encoder;
        this.urlValidator = urlValidator;
        this.aliasValidator = aliasValidator;
        this.cacheService = cacheService;
        this.clock = clock;
        this.publicBaseUrl = publicBaseUrl;
    }

    @Transactional
    public CreateShortUrlResponse create(CreateShortUrlRequest request) {
        URI normalizedUrl = urlValidator.validatePublicHttpUrl(request.longUrl());
        String customAlias = normalizeAlias(request.customAlias());

        if (customAlias != null) {
            aliasValidator.validateNotReserved(customAlias);
            return createWithCustomAlias(normalizedUrl, customAlias, request.expiresAt());
        }

        return createWithGeneratedCode(normalizedUrl, request.expiresAt());
    }

    @Transactional(readOnly = true)
    public String resolveLongUrl(String shortCode) {
        return cacheService.getLongUrl(shortCode)
            .orElseGet(() -> resolveFromDatabaseAndCache(shortCode));
    }

    @Transactional(readOnly = true)
    public UrlDetailsResponse getDetails(String shortCode) {
        ShortUrl entity = repository.findByShortCode(shortCode)
            .orElseThrow(() -> new ShortUrlNotFoundException(shortCode));

        return toDetailsResponse(entity);
    }

    @Transactional
    public UrlDetailsResponse block(String shortCode, String reason) {
        ShortUrl entity = repository.findByShortCode(shortCode)
            .orElseThrow(() -> new ShortUrlNotFoundException(shortCode));

        entity.block(reason, Instant.now(clock));
        ShortUrl saved = repository.save(entity);
        cacheService.evict(shortCode);
        return toDetailsResponse(saved);
    }

    @Transactional
    public UrlDetailsResponse unblock(String shortCode) {
        ShortUrl entity = repository.findByShortCode(shortCode)
            .orElseThrow(() -> new ShortUrlNotFoundException(shortCode));

        entity.unblock();
        ShortUrl saved = repository.save(entity);
        if (!saved.isExpired(Instant.now(clock))) {
            cacheService.putLongUrl(saved.getShortCode(), saved.getLongUrl(), saved.getExpiresAt());
        } else {
            cacheService.evict(shortCode);
        }
        return toDetailsResponse(saved);
    }

    private CreateShortUrlResponse createWithGeneratedCode(URI normalizedUrl, Instant expiresAt) {
        Long id = repository.nextId();
        String shortCode = base62Encoder.encode(id);

        ShortUrl entity = new ShortUrl(id, shortCode, normalizedUrl.toString(), expiresAt);
        ShortUrl saved = repository.saveAndFlush(entity);

        cacheIfActive(saved);
        return toCreateResponse(saved);
    }

    private CreateShortUrlResponse createWithCustomAlias(URI normalizedUrl, String customAlias, Instant expiresAt) {
        if (repository.existsByShortCode(customAlias)) {
            throw new CustomAliasAlreadyExistsException(customAlias);
        }

        try {
            Long id = repository.nextId();
            ShortUrl entity = new ShortUrl(id, customAlias, normalizedUrl.toString(), expiresAt);
            ShortUrl saved = repository.saveAndFlush(entity);
            cacheIfActive(saved);
            return toCreateResponse(saved);
        } catch (DataIntegrityViolationException exception) {
            throw new CustomAliasAlreadyExistsException(customAlias);
        }
    }

    private String resolveFromDatabaseAndCache(String shortCode) {
        ShortUrl entity = repository.findByShortCode(shortCode)
            .orElseThrow(() -> new ShortUrlNotFoundException(shortCode));

        validateResolvable(entity, shortCode);
        cacheService.putLongUrl(entity.getShortCode(), entity.getLongUrl(), entity.getExpiresAt());
        return entity.getLongUrl();
    }

    private void validateResolvable(ShortUrl entity, String shortCode) {
        if (entity.getStatus() != UrlStatus.ACTIVE) {
            cacheService.evict(shortCode);
            throw new ShortUrlGoneException(shortCode, "status=" + entity.getStatus());
        }

        Instant now = Instant.now(clock);
        if (entity.isExpired(now)) {
            cacheService.evict(shortCode);
            throw new ShortUrlGoneException(shortCode, "expired");
        }
    }

    private void cacheIfActive(ShortUrl saved) {
        if (saved.getStatus() == UrlStatus.ACTIVE && !saved.isExpired(Instant.now(clock))) {
            cacheService.putLongUrl(saved.getShortCode(), saved.getLongUrl(), saved.getExpiresAt());
        }
    }

    private CreateShortUrlResponse toCreateResponse(ShortUrl saved) {
        return new CreateShortUrlResponse(
            saved.getId(),
            saved.getShortCode(),
            buildShortUrl(saved.getShortCode()),
            saved.getLongUrl(),
            saved.getExpiresAt(),
            saved.getCreatedAt()
        );
    }

    private UrlDetailsResponse toDetailsResponse(ShortUrl entity) {
        return new UrlDetailsResponse(
            entity.getId(),
            entity.getShortCode(),
            buildShortUrl(entity.getShortCode()),
            entity.getLongUrl(),
            entity.getStatus(),
            entity.getExpiresAt(),
            entity.getCreatedAt(),
            entity.getUpdatedAt(),
            entity.getBlockedReason(),
            entity.getBlockedAt()
        );
    }

    private String buildShortUrl(String shortCode) {
        return UriComponentsBuilder.fromUriString(publicBaseUrl)
            .pathSegment(shortCode)
            .build()
            .toUriString();
    }

    private String normalizeAlias(String customAlias) {
        if (customAlias == null || customAlias.isBlank()) {
            return null;
        }
        return customAlias.trim().toLowerCase(Locale.ROOT);
    }
}
