package pl.jakubtworek.marketplace.delivery;

/**
 * Wykonywalna polityka progressive delivery. Canary nie jest „małym rolloutem”,
 * jeśli nikt automatycznie nie porównuje go z baseline. Decyzja wymaga minimalnej
 * próbki, limitu bezwzględnego oraz limitu regresji, aby bardzo dobry baseline nie
 * maskował pogorszenia. UNKNOWN zatrzymuje promocję, zamiast zgadywać przy braku danych.
 */
public record CanaryDecisionPolicy(long minimumRequests, double maxErrorRate,
                                   double maxP99Millis, double maxRelativeRegression) {
    public enum Decision { PROMOTE, ROLLBACK, INCONCLUSIVE }
    public record Signals(long requests, double errorRate, double p99Millis) {}

    public Decision evaluate(Signals baseline, Signals canary) {
        if (canary.requests() < minimumRequests) return Decision.INCONCLUSIVE;
        boolean absoluteFailure = canary.errorRate() > maxErrorRate || canary.p99Millis() > maxP99Millis;
        boolean relativeFailure = canary.errorRate() > baseline.errorRate() * (1 + maxRelativeRegression)
                || canary.p99Millis() > baseline.p99Millis() * (1 + maxRelativeRegression);
        return absoluteFailure || relativeFailure ? Decision.ROLLBACK : Decision.PROMOTE;
    }
}
