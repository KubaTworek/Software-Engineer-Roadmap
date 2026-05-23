package pl.jakubtworek.backend_engineering.stage_3.block_a.metrics.src.main.java.com.example.systemdesign.metrics;

/**
 * Models the effect of cache hit ratio on database read load.
 */
public final class CacheImpactModel {

    private CacheImpactModel() {
        // Utility class.
    }

    /**
     * Converts hit ratio to miss ratio.
     */
    public static double missRatio(double hitRatio) {
        if (hitRatio < 0 || hitRatio > 1) {
            throw new IllegalArgumentException("hitRatio must be in range [0, 1]");
        }
        return 1.0 - hitRatio;
    }

    /**
     * Calculates how many read queries reach the database after cache.
     */
    public static double databaseReadQps(double rps, double hitRatio, int queriesOnMiss) {
        return CapacityFormula.databaseReadQpsAfterCache(
                rps,
                missRatio(hitRatio),
                queriesOnMiss
        );
    }

    /**
     * Calculates the read query savings produced by cache.
     */
    public static double savedReadQps(double rps, double hitRatio, int queriesPerRequestWithoutCache) {
        if (rps < 0) throw new IllegalArgumentException("rps must be non-negative");
        if (hitRatio < 0 || hitRatio > 1) throw new IllegalArgumentException("hitRatio must be in range [0, 1]");
        if (queriesPerRequestWithoutCache < 0) throw new IllegalArgumentException("queriesPerRequestWithoutCache must be non-negative");

        double withoutCache = rps * queriesPerRequestWithoutCache;
        double withCache = databaseReadQps(rps, hitRatio, queriesPerRequestWithoutCache);
        return withoutCache - withCache;
    }
}
