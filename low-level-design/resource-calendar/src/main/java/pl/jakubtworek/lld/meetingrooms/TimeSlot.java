package pl.jakubtworek.lld.meetingrooms;

import java.time.Instant;
import java.util.Objects;

/**
 * Niepusty przedział chwil [start, end). Koniec jest wyłączony, więc 10–11
 * i 11–12 nie kolidują. Instant opisuje jednoznaczną chwilę, bez domyślnej
 * strefy komputera. Konwersja czasu lokalnego ze strefą należy do adaptera.
 * Typ nie sprawdza „przyszłości”: dawny przedział nadal jest poprawną wartością
 * w historii. Dopiero operacja rezerwacji porównuje go z wstrzykniętym zegarem.
 */
public record TimeSlot(Instant start, Instant end) {

    public TimeSlot {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(end, "end");
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("Początek musi poprzedzać koniec");
        }
    }

    public boolean overlaps(TimeSlot other) {
        Objects.requireNonNull(other, "other");
        // Sprawdzanie wyłącznie początku przeoczyłoby przedział zawierający
        // drugi w całości. Dwie ścisłe nierówności obejmują też ten przypadek.
        return start.isBefore(other.end) && other.start.isBefore(end);
    }
}
