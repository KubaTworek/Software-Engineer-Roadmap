package pl.jakubtworek.lld.meetingrooms;

import java.util.Map;

/**
 * Jeden widok katalogu i rezerwacji. Kopie map wystarczają, ponieważ wartości
 * i ich zbiory także są niemutowalne. Snapshot nie jest kopią zegara: odczyt
 * starego widoku pokazuje dawną publikację, a nie ponowną ocenę dostępności.
 * Brak setterów zapobiega dopisaniu spotkania z pominięciem walidacji usługi.
 */
public final class BookingSnapshot {

    private final Map<RoomId, MeetingRoom> rooms;
    private final Map<BookingId, Booking> bookings;

    BookingSnapshot(Map<RoomId, MeetingRoom> rooms, Map<BookingId, Booking> bookings) {
        this.rooms = Map.copyOf(rooms);
        this.bookings = Map.copyOf(bookings);
    }

    public Map<RoomId, MeetingRoom> rooms() {
        return rooms;
    }

    public Map<BookingId, Booking> bookings() {
        return bookings;
    }
}
