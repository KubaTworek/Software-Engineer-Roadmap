package pl.jakubtworek.lld.cache.domain;

/**
 * Monotoniczny czas względny w nanosekundach: nieujemny i niemalejący.
 * Nie jest datą UTC. Systemowy adapter mierzy różnicę nanoTime od utworzenia;
 * zakres jednego adaptera jest ograniczony do Long.MAX_VALUE ns (około 292 lat).
 * Cache odrzuca cofnięcie zamiast po cichu wydłużać TTL.
 */
@FunctionalInterface
public interface Ticker {

    long read();

    static Ticker systemTicker() {
        long origin = System.nanoTime();
        return () -> System.nanoTime() - origin;
    }
}
