package pl.jakubtworek.lld.meetingrooms;

import java.time.Duration;
import java.util.Objects;

/**
 * Czas przygotowania PO spotkaniu jest osobną wartością, nie zmianą jego
 * rzeczywistej godziny końca. Stały bufor całego kalendarza rozszerza KAŻDY
 * porównywany termin. Dzięki temu wcześniejsze nowe spotkanie też musi
 * zostawić czas na przygotowanie przed istniejącym późniejszym spotkaniem.
 */
public record TurnaroundBuffer(Duration after) {

    public TurnaroundBuffer {
        Objects.requireNonNull(after, "after");
        if (after.isNegative()) {
            throw new IllegalArgumentException("Bufor nie może być ujemny");
        }
    }

    public static TurnaroundBuffer none() {
        return new TurnaroundBuffer(Duration.ZERO);
    }

    public TimeSlot blockingSlot(TimeSlot meeting) {
        Objects.requireNonNull(meeting, "meeting");
        // Nie obcinamy końca do Instant.MAX. Niemieszczący się przedział to
        // błąd danych (DateTimeException/ArithmeticException), przed publikacją.
        return new TimeSlot(meeting.start(), meeting.end().plus(after));
    }
}
