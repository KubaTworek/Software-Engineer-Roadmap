package pl.jakubtworek.lld.cache.domain;

/**
 * Liczniki od utworzenia lub resetStatistics. Hit/miss liczy tylko get.
 * Expirations to fizyczne usunięcia przez sprzątanie, evictions obejmuje
 * put i resize. Clear ma osobny licznik, także dla niesprzątanych wygasłych.
 * Przy Long.MAX_VALUE liczniki nasycają się zamiast przepełniać.
 */
public record CacheStatistics(long hits, long misses, long expirations, long evictions, long invalidations,
        long clearRemovals) {

    public CacheStatistics {
        if (hits < 0 || misses < 0 || expirations < 0 || evictions < 0 || invalidations < 0 || clearRemovals < 0) {
            throw new IllegalArgumentException("Ujemna statystyka");
        }
    }
}
