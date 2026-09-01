package pl.jakubtworek.backend_engineering.stage_3.block_a.metrics.cache;

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
        if (!Double.isFinite(rps) || rps < 0) throw new IllegalArgumentException("rps must be finite and non-negative");
        if (!Double.isFinite(missRatio) || missRatio < 0 || missRatio > 1) throw new IllegalArgumentException("missRatio must be finite and in range [0, 1]");
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
        if (!Double.isFinite(hitRatio) || hitRatio < 0 || hitRatio > 1) {
            throw new IllegalArgumentException("hitRatio must be finite and in range [0, 1]");
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
