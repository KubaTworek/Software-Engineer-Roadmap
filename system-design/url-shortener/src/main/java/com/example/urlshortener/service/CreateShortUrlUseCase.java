package com.example.urlshortener.service;

import com.example.urlshortener.dto.CreateShortUrlRequest;
import com.example.urlshortener.dto.CreateShortUrlResponse;
import com.example.urlshortener.exception.CustomAliasAlreadyExistsException;
import com.example.urlshortener.model.ShortUrl;
import com.example.urlshortener.outbox.UrlProjectionOutboxService;
import com.example.urlshortener.region.RegionProperties;
import com.example.urlshortener.repository.ShortUrlRepository;
import com.example.urlshortener.validation.AliasValidator;
import com.example.urlshortener.validation.UrlValidator;
import java.net.URI;
import java.time.Instant;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreateShortUrlUseCase {
    private final ShortUrlRepository repository;
    private final Base62Encoder encoder;
    private final UrlValidator urls;
    private final AliasValidator aliases;
    private final RegionProperties region;
    private final UrlProjectionOutboxService outbox;
    private final UrlLookupFactory lookupFactory;
    private final ShortUrlResponseMapper responses;

    public CreateShortUrlUseCase(ShortUrlRepository repository, Base62Encoder encoder, UrlValidator urls,
            AliasValidator aliases, RegionProperties region, UrlProjectionOutboxService outbox,
            UrlLookupFactory lookupFactory, ShortUrlResponseMapper responses) {
        this.repository = repository;
        this.encoder = encoder;
        this.urls = urls;
        this.aliases = aliases;
        this.region = region;
        this.outbox = outbox;
        this.lookupFactory = lookupFactory;
        this.responses = responses;
    }

    @Transactional
    public CreateShortUrlResponse create(CreateShortUrlRequest request) {
        return create(request, null);
    }

    @Transactional
    public CreateShortUrlResponse create(CreateShortUrlRequest request, Long ownerId) {
        if (!region.isPrimaryRegion()) {
            throw new IllegalArgumentException("This region does not accept writes. Primary region: " + region.getPrimaryRegion());
        }
        URI target = urls.validatePublicHttpUrl(request.longUrl());
        String alias = normalizeAlias(request.customAlias());
        return alias == null ? generated(target, request.expiresAt(), ownerId) : custom(target, alias, request.expiresAt(), ownerId);
    }

    private CreateShortUrlResponse generated(URI target, Instant expiresAt, Long ownerId) {
        Long id = repository.nextId();
        return persist(owned(new ShortUrl(id, encoder.encode(id), target.toString(), expiresAt), ownerId));
    }

    private CreateShortUrlResponse custom(URI target, String alias, Instant expiresAt, Long ownerId) {
        aliases.validateNotReserved(alias);
        if (repository.existsByShortCode(alias)) throw new CustomAliasAlreadyExistsException(alias);
        try {
            return persist(owned(new ShortUrl(repository.nextId(), alias, target.toString(), expiresAt), ownerId));
        } catch (DataIntegrityViolationException raceWinnerAlreadyCommitted) {
            // The pre-check improves the message; only the unique constraint closes the race.
            throw new CustomAliasAlreadyExistsException(alias);
        }
    }

    private ShortUrl owned(ShortUrl url, Long ownerId) {
        if (ownerId != null) url.assignOwner(ownerId);
        return url;
    }

    private CreateShortUrlResponse persist(ShortUrl candidate) {
        ShortUrl saved = repository.saveAndFlush(candidate);
        // Source row and intent are atomic; all derived stores are updated after commit.
        outbox.enqueue(lookupFactory.from(saved));
        return responses.created(saved);
    }

    private String normalizeAlias(String alias) {
        if (alias == null || alias.isBlank()) return null;
        return alias.trim().toLowerCase(Locale.ROOT);
    }
}
