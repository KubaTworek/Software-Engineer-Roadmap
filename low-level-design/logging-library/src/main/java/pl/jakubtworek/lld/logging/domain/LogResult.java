package pl.jakubtworek.lld.logging.domain;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Raport w kolejności konfiguracji. Brak event oznacza odfiltrowanie przed
 * obliczeniem komunikatu i czasu. Nie jest dziennikiem utrwalanym przez logger.
 */
public record LogResult(Optional<LogEvent> event, List<Delivery> deliveries) {

    public LogResult {
        Objects.requireNonNull(event);
        deliveries = List.copyOf(deliveries);
    }
}
