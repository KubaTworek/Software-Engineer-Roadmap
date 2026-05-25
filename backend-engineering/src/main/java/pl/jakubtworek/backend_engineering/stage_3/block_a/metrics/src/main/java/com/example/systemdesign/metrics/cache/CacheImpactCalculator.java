package pl.jakubtworek.backend_engineering.stage_3.block_a.metrics.src.main.java.com.example.systemdesign.metrics.cache;

/**
 * Calculates how much database read traffic remains after cache.
 */
public final class CacheImpactCalculator {

    private CacheImpactCalculator() {
    }

    /**
     * DB_read_QPS = RPS * miss_ratio * queries_on_miss
     */
    public static double databaseReadQpsAfterCache(
            double rps,
            double missRatio,
            int queriesOnMiss
    ) {
        if (rps < 0) throw new IllegalArgumentException("rps must be non-negative");
        if (missRatio < 0 || missRatio > 1) throw new IllegalArgumentException("missRatio must be in range [0, 1]");
        if (queriesOnMiss < 0) throw new IllegalArgumentException("queriesOnMiss must be non-negative");

        return rps * missRatio * queriesOnMiss;
    }

    /**
     * Calculates read QPS avoided by cache.
     */
    public static double savedDatabaseReadQps(
            double rps,
            double hitRatio,
            int queriesWithoutCachePerRequest
    ) {
        if (hitRatio < 0 || hitRatio > 1) {
            throw new IllegalArgumentException("hitRatio must be in range [0, 1]");
        }

        double withoutCache = rps * queriesWithoutCachePerRequest;
        double withCache = databaseReadQpsAfterCache(
                rps,
                1.0 - hitRatio,
                queriesWithoutCachePerRequest
        );

        return withoutCache - withCache;
    }
}