package pl.jakubtworek.backend_engineering.stage_1.block_d.sql.connection_pool;

/**
 * Calculates a safe upper bound for an application's database pool.
 *
 * <p>This is a capacity budget, not a promise that a larger pool improves
 * throughput. The final value still needs to be verified with pool metrics,
 * database saturation and a representative load test.</p>
 */
public record ConnectionPoolBudget(
        int databaseConnectionLimit,
        int reservedConnections,
        int applicationInstances,
        int targetUtilizationPercent
) {

    public ConnectionPoolBudget {
        if (databaseConnectionLimit <= 0) {
            throw new IllegalArgumentException("databaseConnectionLimit must be positive");
        }
        if (reservedConnections < 0 || reservedConnections >= databaseConnectionLimit) {
            throw new IllegalArgumentException(
                    "reservedConnections must be between 0 and databaseConnectionLimit - 1"
            );
        }
        if (applicationInstances <= 0) {
            throw new IllegalArgumentException("applicationInstances must be positive");
        }
        if (targetUtilizationPercent <= 0 || targetUtilizationPercent > 100) {
            throw new IllegalArgumentException("targetUtilizationPercent must be between 1 and 100");
        }
    }

    /** Connections available to all application instances after both safety margins. */
    public int applicationConnectionBudget() {
        int afterOperationalReserve = databaseConnectionLimit - reservedConnections;
        return afterOperationalReserve * targetUtilizationPercent / 100;
    }

    /** Maximum pool size per instance when every instance gets the same budget. */
    public int maxPoolSizePerInstance() {
        return applicationConnectionBudget() / applicationInstances;
    }

    /** Budget deliberately left undistributed because an equal split rounds down. */
    public int undistributedConnections() {
        return applicationConnectionBudget() % applicationInstances;
    }

    /**
     * Little's Law estimate: concurrency = throughput * time in the database.
     * It is a starting point for measurement, not a replacement for a load test.
     */
    public static int estimatedConcurrentDatabaseWork(
            double requestsPerSecond,
            double averageDatabaseTimeMillis
    ) {
        if (!Double.isFinite(requestsPerSecond) || requestsPerSecond < 0) {
            throw new IllegalArgumentException("requestsPerSecond must be finite and non-negative");
        }
        if (!Double.isFinite(averageDatabaseTimeMillis) || averageDatabaseTimeMillis < 0) {
            throw new IllegalArgumentException("averageDatabaseTimeMillis must be finite and non-negative");
        }
        return (int) Math.ceil(requestsPerSecond * averageDatabaseTimeMillis / 1_000.0);
    }
}
