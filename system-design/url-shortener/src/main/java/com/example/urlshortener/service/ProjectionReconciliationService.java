package com.example.urlshortener.service;

import com.example.urlshortener.repository.ShortUrlRepository;
import com.example.urlshortener.storage.UrlLookupRecord;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Compares rebuildable Redis state with the PostgreSQL source of truth.
 * Dry-run is the default operational posture; repair only reapplies missing,
 * stale or divergent rows and never overwrites a projection ahead of source.
 */
@Service
public class ProjectionReconciliationService {
    private final ShortUrlRepository source;
    private final UrlLookupFactory lookupFactory;
    private final ShortUrlCacheService cache;

    public ProjectionReconciliationService(ShortUrlRepository source, UrlLookupFactory lookupFactory,
            ShortUrlCacheService cache) {
        this.source = source;
        this.lookupFactory = lookupFactory;
        this.cache = cache;
    }

    @Transactional(readOnly = true)
    public ProjectionReconciliationReport reconcile(int limit, boolean repair) {
        if (limit < 1 || limit > 10_000) {
            throw new IllegalArgumentException("Reconciliation limit must be between 1 and 10000");
        }
        int matching = 0, missing = 0, stale = 0, divergent = 0, ahead = 0, repaired = 0;
        var urls = source.findAll(PageRequest.of(0, limit)).getContent();
        for (var url : urls) {
            UrlLookupRecord expected = lookupFactory.from(url);
            var actual = cache.get(expected.shortCode());
            if (actual.isEmpty()) {
                missing++;
                if (repair && cache.put(expected)) repaired++;
                continue;
            }
            UrlLookupRecord cached = actual.orElseThrow();
            if (cached.version() < expected.version()) {
                stale++;
                if (repair && cache.put(expected)) repaired++;
            } else if (cached.version() > expected.version()) {
                // A primary-derived projection should never lead its source.
                // Do not destroy evidence automatically; investigate first.
                ahead++;
            } else if (!sameState(cached, expected)) {
                divergent++;
                if (repair && cache.put(expected)) repaired++;
            } else {
                matching++;
            }
        }
        return new ProjectionReconciliationReport(
                urls.size(), matching, missing, stale, divergent, ahead, repaired);
    }

    private boolean sameState(UrlLookupRecord left, UrlLookupRecord right) {
        return java.util.Objects.equals(left.longUrl(), right.longUrl())
                && left.status() == right.status()
                && java.util.Objects.equals(left.expiresAt(), right.expiresAt());
    }
}
