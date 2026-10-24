package pl.jakubtworek.lld.meetingrooms;

import pl.jakubtworek.lld.calendar.IntervalCalendar;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import static pl.jakubtworek.lld.meetingrooms.BookingException.Reason.*;

/**
 * Właściciel kalendarza: tylko tutaj cechy sal, kolizje i wybór spotykają się
 * przed publikacją. Model jest JEDNOWĄTKOWY, w pamięci; sprawdzenie i zapis
 * nie stanowią transakcji między wątkami. Clock wstrzykujemy, bo „przyszły
 * termin” jest regułą domeny, a nie szczegółem konsoli.
 */
public final class BookingService {

    private final Clock clock;
    private final RoomSelectionPolicy selection;
    private final TurnaroundBuffer buffer;
    private final Map<RoomId, MeetingRoom> rooms;
    private final List<MeetingRoom> orderedRooms;
    private final Map<BookingId, Booking> bookings = new HashMap<>();
    private final Map<RoomId, IntervalCalendar<Instant, BookingId, Booking>> calendars = new HashMap<>();
    private BookingSnapshot cached;

    public BookingService(List<MeetingRoom> rooms, Clock clock, RoomSelectionPolicy selection) {
        this(rooms, clock, selection, TurnaroundBuffer.none());
    }

    /**
     * Bufor jest stały dla instancji; nie zmieniamy znaczenia już przyjętych terminów w trakcie pracy.
     */
    public BookingService(List<MeetingRoom> rooms, Clock clock, RoomSelectionPolicy selection,
            TurnaroundBuffer buffer) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.selection = Objects.requireNonNull(selection, "selection");
        this.buffer = Objects.requireNonNull(buffer, "buffer");
        var catalog = new HashMap<RoomId, MeetingRoom>();
        for (var room : List.copyOf(rooms)) {
            if (catalog.putIfAbsent(room.id(), room) != null) {
                throw new IllegalArgumentException("Powtórzony kod sali");
            }
        }
        if (catalog.isEmpty()) {
            throw new IllegalArgumentException("Katalog sal nie może być pusty");
        }
        this.rooms = Map.copyOf(catalog);
        // Map.copyOf nie zapewnia kolejności. Raz materializujemy osobną,
        // uporządkowaną listę; Stream.toList nie pozwala jej później modyfikować.
        orderedRooms = catalog.values().stream().sorted(Comparator.comparing(room -> room.id().value())).toList();
        this.rooms.keySet().forEach(id -> calendars.put(id, new IntervalCalendar<>()));
    }

    public BookingSnapshot snapshot() {
        if (cached == null) {
            cached = new BookingSnapshot(rooms, bookings);
        }
        return cached;
    }

    public TurnaroundBuffer buffer() {
        return buffer;
    }

    /**
     * Wyszukiwanie nie rezerwuje wyniku. book zawsze sprawdza kalendarz ponownie.
     */
    public List<MeetingRoom> availableRooms(RoomRequirements requirements, TimeSlot slot) {
        return analyzeAvailability(requirements, slot).availableRooms();
    }

    public AvailabilityReport analyzeAvailability(RoomRequirements requirements, TimeSlot slot) {
        Objects.requireNonNull(requirements, "requirements");
        Objects.requireNonNull(slot, "slot");
        futureStart(slot, clock.instant());
        return analyze(requirements, slot, null, null);
    }

    public Booking book(UserId organizer, RoomRequirements requirements, TimeSlot slot, String subject) {
        Objects.requireNonNull(organizer, "organizer");
        Objects.requireNonNull(requirements, "requirements");
        Objects.requireNonNull(slot, "slot");
        String normalized = Objects.requireNonNull(subject, "subject").strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Temat nie może być pusty");
        }
        // Jedna obserwacja czasu na operację. Ponowne czytanie zegara w środku
        // walidacji mogłoby dać dwa różne rozstrzygnięcia na tej samej granicy.
        futureStart(slot, clock.instant());
        var report = analyze(requirements, slot, null, null);
        var available = report.availableRooms();
        if (available.isEmpty()) {
            throw new BookingException(NO_AVAILABLE_ROOM, "Brak sali spełniającej wymagania i termin", report);
        }
        var selected = selection.select(available);
        // Porównujemy całą wartość sali, nie sam kod: obca strategia nie może
        // podmienić pojemności lub sprzętu ani wskazać sali spoza kandydatów.
        if (selected == null || !available.contains(selected)) {
            throw new IllegalStateException("Strategia wybrała salę spoza kandydatów");
        }
        var booking = new Booking(new BookingId(UUID.randomUUID()), selected.id(), organizer, slot, requirements,
                normalized, BookingStatus.CONFIRMED);
        publish(booking);
        return booking;
    }

    public Booking cancel(BookingId id, UserId actor) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(actor, "actor");
        var booking = bookings.get(id);
        if (booking == null) {
            throw new BookingException(UNKNOWN_BOOKING, "Nieznana rezerwacja");
        }
        var cancelled = booking.cancel(actor, clock.instant());
        publish(cancelled);
        return cancelled;
    }

    /**
     * Zmiana w tej samej sali. Strategia wyboru nie przenosi spotkania ukradkiem do innego zasobu.
     */
    public Booking reschedule(BookingId id, UserId actor, TimeSlot proposed) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(proposed, "proposed");
        var original = bookings.get(id);
        if (original == null) {
            throw new BookingException(UNKNOWN_BOOKING, "Nieznana rezerwacja");
        }
        var candidate = original.reschedule(actor, proposed, clock.instant());
        var report = analyze(candidate.requirements(), proposed, id, original.roomId());
        if (report.availableRooms().isEmpty()) {
            throw new BookingException(NO_AVAILABLE_ROOM, "Dotychczasowa sala jest niedostępna w nowym terminie",
                    report);
        }
        // Identyczny termin też przechodzi walidację, lecz nie tworzy zbędnej
        // publikacji. To nie jest ogólny mechanizm idempotencji poleceń.
        if (candidate.equals(original)) {
            return original;
        }
        publish(candidate);
        return candidate;
    }

    private AvailabilityReport analyze(RoomRequirements requirements, TimeSlot slot, BookingId excluded,
            RoomId onlyRoom) {
        // Przy nieprzecinających się blokadach tylko poprzednik początku może
        // nachodzić z lewej. Dalej idziemy po kolejnych węzłach aż do końca zapytania.
        // Koszt na salę: O(log B + K), gdzie K to liczba zwracanych konfliktów.
        var blocking = buffer.blockingSlot(slot);
        var assessments = orderedRooms.stream().filter(room -> onlyRoom == null || onlyRoom.equals(room.id()))
                .map(room -> {
            var conflicts = calendars.get(room.id()).conflicts(blocking.start(), blocking.end(), excluded).stream()
                    .map(Booking::id).toList();
            return RoomAvailability.assess(room, requirements, conflicts);
        }).toList();
        return new AvailabilityReport(requirements, slot, blocking, assessments);
    }

    // Historia i indeks zajętości mają różnych czytelników, ale jednego
    // właściciela zmian. Synchronizacja samej mapy nie wystarczyłaby dla obu.
    private void publish(Booking booking) {
        // Blokujący koniec został sprawdzony przed tą sekcją. Stary indeks
        // usuwamy dopiero po sukcesie walidacji całej operacji.
        var tree = calendars.get(booking.roomId());
        var previous = bookings.get(booking.id());
        if (previous != null && previous.status() == BookingStatus.CONFIRMED) {
            tree.remove(previous.id());
        }
        bookings.put(booking.id(), booking);
        if (booking.status() == BookingStatus.CONFIRMED) {
            var blocking = buffer.blockingSlot(booking.slot());
            tree.put(booking.id(), blocking.start(), blocking.end(), booking);
        }
        cached = null;
    }

    private static void futureStart(TimeSlot slot, Instant now) {
        if (slot.start().isBefore(now)) {
            throw new BookingException(PAST_START, "Początek spotkania jest w przeszłości");
        }
    }
}
