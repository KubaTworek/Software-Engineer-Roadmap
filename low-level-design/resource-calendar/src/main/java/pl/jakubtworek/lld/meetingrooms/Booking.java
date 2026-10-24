package pl.jakubtworek.lld.meetingrooms;

import java.time.Instant;
import java.util.Objects;
import static pl.jakubtworek.lld.meetingrooms.BookingException.Reason.*;

/**
 * Niemutowalna rezerwacja chroni zmianę terminu i przejście CONFIRMED → CANCELLED.
 * Zna organizatora i termin, lecz nie potrafi samodzielnie zagwarantować braku
 * kolizji z innymi spotkaniami — to reguła całego kalendarza w BookingService.
 * cancel zwraca nowy obiekt o tym samym ID; wcześniej pobrany widok nie zmienia
 * statusu w rękach czytelnika. Konstruktor wartości nie dodaje jej do kalendarza.
 */
public record Booking(BookingId id, RoomId roomId, UserId organizer, TimeSlot slot, RoomRequirements requirements,
        String subject, BookingStatus status) {

    public Booking {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(roomId, "roomId");
        Objects.requireNonNull(organizer, "organizer");
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(requirements, "requirements");
        Objects.requireNonNull(status, "status");
        subject = Objects.requireNonNull(subject, "subject").strip();
        if (subject.isEmpty()) {
            throw new IllegalArgumentException("Temat nie może być pusty");
        }
    }

    public Booking cancel(UserId actor, Instant now) {
        requireEditable(actor, now);
        return new Booking(id, roomId, organizer, slot, requirements, subject, BookingStatus.CANCELLED);
    }

    /**
     * Zmienia wyłącznie termin, zachowując ID, salę i wymagania. To kandydat:
     * usługa musi jeszcze sprawdzić go wobec innych rezerwacji. Nie publikujemy
     * „anuluj, potem zarezerwuj”, bo odmowa drugiego kroku utraciłaby stary termin.
     */
    public Booking reschedule(UserId actor, TimeSlot proposed, Instant now) {
        Objects.requireNonNull(proposed, "proposed");
        requireEditable(actor, now);
        if (proposed.start().isBefore(now)) {
            throw new BookingException(PAST_START, "Nowy początek jest w przeszłości");
        }
        return new Booking(id, roomId, organizer, proposed, requirements, subject, status);
    }

    private void requireEditable(UserId actor, Instant now) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(now, "now");
        if (!organizer.equals(actor)) {
            throw new BookingException(NOT_ORGANIZER, "Zmiany może zgłosić organizator");
        }
        if (status != BookingStatus.CONFIRMED) {
            throw new BookingException(NOT_CONFIRMED, "Rezerwacja jest już anulowana");
        }
        if (!now.isBefore(slot.start())) {
            throw new BookingException(ALREADY_STARTED, "Nie zmieniamy rozpoczętego spotkania");
        }
    }
}
