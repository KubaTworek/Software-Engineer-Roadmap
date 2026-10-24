package pl.jakubtworek.lld.logging.sink;

/**
 * Czysty odczyt stanu bufora. Liczniki dotyczą życia instancji i nie zerują się
 * przez clear; przy Long.MAX_VALUE nasycają się. Nie są statystykami całego loggera.
 */
public record MemorySinkDiagnostics(int capacity, OverflowPolicy policy, int size, long rejected, long dropped) {
}
