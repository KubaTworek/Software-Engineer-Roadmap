package com.example.urlshortener.service;

import com.example.urlshortener.exception.ShortUrlGoneException;
import com.example.urlshortener.exception.ShortUrlNotFoundException;
import com.example.urlshortener.model.ShortUrl;
import com.example.urlshortener.model.UrlStatus;
import com.example.urlshortener.observability.UrlShortenerMetrics;
import com.example.urlshortener.repository.ShortUrlRepository;
import com.example.urlshortener.storage.UrlLookupRecord;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResolveShortUrlUseCase {
    private final ShortUrlRepository repository;
    private final ShortUrlCacheService cache;
    private final UrlLookupFactory lookupFactory;
    private final UrlShortenerMetrics metrics;
    private final Clock clock;

    public ResolveShortUrlUseCase(ShortUrlRepository repository, ShortUrlCacheService cache,
            UrlLookupFactory lookupFactory, UrlShortenerMetrics metrics, Clock clock) {
        this.repository = repository;
        this.cache = cache;
        this.lookupFactory = lookupFactory;
        this.metrics = metrics;
        this.clock = clock;
    }

    /** A cache miss reads primary, not a lagging replica, preserving read-your-writes. */
    @Transactional
    public String resolve(String code) {
        var cached = cache.get(code);
        if (cached.isPresent()) {
            metrics.cacheHit();
            return resolvable(cached.orElseThrow(), code);
        }
        metrics.cacheMiss();
        ShortUrl source = repository.findByShortCode(code).orElseThrow(() -> new ShortUrlNotFoundException(code));
        validate(source.getStatus(), source.isExpired(Instant.now(clock)), code);
        cache.put(lookupFactory.from(source));
        return source.getLongUrl();
    }

    private String resolvable(UrlLookupRecord record, String code) {
        validate(record.status(), record.isExpired(Instant.now(clock)), code);
        return record.longUrl();
    }

    private void validate(UrlStatus status, boolean expired, String code) {
        if (status != UrlStatus.ACTIVE || expired) {
            // BLOCKED/DELETED/expired records are intentional negative-cache
            // entries. Removing them here would turn every rejected redirect into
            // a primary-database read and weaken protection against stale replay.
            throw new ShortUrlGoneException(code, expired ? "expired" : "status=" + status);
        }
    }
}
