package pl.jakubtworek.lld.logging.domain;

/**
 * Wymienny, czysty sposób reprezentacji zdarzenia. Zwraca jeden rekord bez
 * końcowego separatora linii; sink odpowiada za sposób zapisu. Awaria formatowania
 * jest błędem przygotowania i następuje przed pierwszym wyjściem.
 */
@FunctionalInterface
public interface LogFormatter {

    String format(LogEvent event);
}
