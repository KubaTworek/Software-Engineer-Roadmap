package pl.jakubtworek.lld.meetingrooms;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.time.Instant;
import java.time.DateTimeException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static pl.jakubtworek.lld.meetingrooms.Fixtures.*;
import static pl.jakubtworek.lld.meetingrooms.BookingException.Reason.*;

/**
 * Przypadki rozszerzeń sprawdzają wspólnie termin spotkania, blokadę zasobu i brak częściowego zapisu.
 */
class BookingExtensionsTest {

    @Test
    void rescheduleKeepsIdentityRoomAndDetailsWhileIgnoringOwnOldInterval() {
        var clock = new MutableClock();
        var service = service(clock);
        var original = service.book(ALA, people(2), slot(1, 3), "Planowanie");
        var before = service.snapshot();
        var moved = service.reschedule(original.id(), ALA, slot(2, 4));
        assertEquals(original.id(), moved.id());
        assertEquals(original.roomId(), moved.roomId());
        assertEquals(original.requirements(), moved.requirements());
        assertEquals(original.subject(), moved.subject());
        assertEquals(1, service.snapshot().bookings().size());
        assertEquals(slot(1, 3), before.bookings().get(original.id()).slot());
        assertEquals(slot(2, 4), moved.slot());
        var after = service.snapshot();
        assertSame(moved, service.reschedule(moved.id(), ALA, moved.slot()));
        assertSame(after, service.snapshot());
        assertTrue(service.availableRooms(people(2), slot(1, 2)).contains(SMALL));
    }

    @Test
    void conflictingMoveCannotSwitchToAnotherRoomAndReportsExactBlocker() {
        var service = service(new MutableClock());
        var original = service.book(ALA, people(2), slot(1, 2), "A");
        var blocker = service.book(BARTEK, people(2), slot(3, 4), "B");
        assertEquals(original.roomId(), blocker.roomId());
        var before = service.snapshot();
        var failure = assertThrows(BookingException.class, () -> service.reschedule(original.id(), ALA, slot(3, 4)));
        assertEquals(NO_AVAILABLE_ROOM, failure.reason());
        assertSame(before, service.snapshot());
        var report = failure.availability().orElseThrow();
        assertEquals(1, report.rooms().size());
        assertEquals(SMALL, report.rooms().getFirst().room());
        assertEquals(List.of(blocker.id()), report.rooms().getFirst().conflicts());
        assertFalse(report.rooms().getFirst().conflicts().contains(original.id()));
        assertEquals(slot(1, 2), service.snapshot().bookings().get(original.id()).slot());
    }

    @Test
    void moveRequiresOwnerConfirmedFutureOriginalAndNonPastNewStart() {
        var clock = new MutableClock();
        var service = service(clock);
        var original = service.book(ALA, people(2), slot(1, 2), "A");
        reject(service, NOT_ORGANIZER, () -> service.reschedule(original.id(), BARTEK, slot(3, 4)));
        reject(service, UNKNOWN_BOOKING, () -> service.reschedule(new BookingId(UUID.randomUUID()), ALA, slot(3, 4)));
        reject(service, PAST_START, () -> service.reschedule(original.id(), ALA, slot(-1, 1)));
        clock.at(slot(1, 2).start());
        reject(service, ALREADY_STARTED, () -> service.reschedule(original.id(), ALA, slot(3, 4)));
        clock.at(NOW);
        service.cancel(original.id(), ALA);
        reject(service, NOT_CONFIRMED, () -> service.reschedule(original.id(), ALA, slot(3, 4)));
    }

    @Test
    void moveMayStartNowAndDoesNotInvokeSelectionPolicy() {
        var calls = new AtomicInteger();
        var service = new BookingService(ROOMS, new MutableClock(), rooms -> {
            calls.incrementAndGet();
            return rooms.getFirst();
        });
        var original = service.book(ALA, people(2), slot(2, 3), "A");
        var moved = service.reschedule(original.id(), ALA, slot(0, 1));
        assertEquals(1, calls.get());
        assertEquals(original.roomId(), moved.roomId());
        reject(service, ALREADY_STARTED, () -> service.cancel(moved.id(), ALA));
    }

    @Test
    void reportListsAllReasonsAndAllConflictsAndMatchesBookingRefusal() {
        var service = service(new MutableClock());
        var a = service.book(ALA, people(2), slot(1, 2), "A");
        var b = service.book(ALA, people(2), slot(2, 3), "B");
        var requirements = new RoomRequirements(99, Set.of(Equipment.PROJECTOR, Equipment.VIDEO_CONFERENCE));
        var before = service.snapshot();
        var report = service.analyzeAvailability(requirements, slot(1, 3));
        assertSame(before, service.snapshot());
        assertEquals(List.of(BIG.id(), SMALL.id(), MEDIUM.id()), report.rooms().stream()
                .map(assessment -> assessment.room().id()).toList());
        var small = report.rooms().get(1);
        assertTrue(small.insufficientCapacity());
        assertEquals(requirements.equipment(), small.missingEquipment());
        assertEquals(List.of(a.id(), b.id()), small.conflicts());
        assertFalse(small.available());
        var failure = assertThrows(BookingException.class, () -> service.book(ALA, requirements, slot(1, 3), "C"));
        assertEquals(report, failure.availability().orElseThrow());
        assertSame(before, service.snapshot());
        assertEquals(report.availableRooms(), service.availableRooms(requirements, slot(1, 3)));
        assertThrows(UnsupportedOperationException.class, () -> report.rooms().clear());
        assertThrows(UnsupportedOperationException.class, () -> small.conflicts().clear());
        assertThrows(UnsupportedOperationException.class, () -> small.missingEquipment().clear());
    }

    @Test
    void reportRemainsOldObservationAfterCancellation() {
        var service = service(new MutableClock());
        var booking = service.book(ALA, people(20), slot(1, 2), "A");
        var report = service.analyzeAvailability(people(20), slot(1, 2));
        service.cancel(booking.id(), ALA);
        assertTrue(report.availableRooms().isEmpty());
        assertEquals(List.of(BIG), service.analyzeAvailability(people(20), slot(1, 2)).availableRooms());
    }

    @Test
    void bufferRejectsTooCloseMeetingsOnBothSidesAndAllowsExactBoundary() {
        var service = buffered();
        var original = service.book(ALA, people(20), slot(2, 3), "A");
        var before = service.snapshot();
        // Żądanie PO starym spotkaniu przecina jego sprzątanie; żądanie PRZED
        // nim samo potrzebuje sprzątania. Rozszerzenie tylko starego wpisu jest błędem.
        reject(service, NO_AVAILABLE_ROOM, () -> service.book(ALA, people(20), slot(3, 4), "Po"));
        reject(service, NO_AVAILABLE_ROOM, () -> service.book(ALA, people(20), slot(1, 2), "Przed"));
        assertSame(before, service.snapshot());
        assertEquals(slot(2, 3), original.slot());
        var boundary = new TimeSlot(slot(3, 4).start().plusSeconds(900), slot(4, 5).start());
        service.book(ALA, people(20), boundary, "Granica");
        var earlier = new TimeSlot(slot(0, 1).start(), slot(2, 3).start().minusSeconds(900));
        service.book(ALA, people(20), earlier, "Przed granicą");
        assertEquals(3, service.snapshot().bookings().size());
    }

    @Test
    void cancellationReleasesBufferAndMoveUsesItForConflictChecks() {
        var service = buffered();
        var first = service.book(ALA, people(20), slot(1, 2), "A");
        var second = service.book(ALA, people(20), slot(3, 4), "B");
        reject(service, NO_AVAILABLE_ROOM, () -> service.reschedule(second.id(), ALA, slot(2, 3)));
        service.cancel(first.id(), ALA);
        var moved = service.reschedule(second.id(), ALA, slot(2, 3));
        assertEquals(second.id(), moved.id());
        assertEquals(slot(2, 3), moved.slot());
    }

    @Test
    void zeroBufferPreservesAdjacencyAndInvalidBufferFailsEarly() {
        var service = service(new MutableClock());
        service.book(ALA, people(20), slot(1, 2), "A");
        service.book(ALA, people(20), slot(2, 3), "B");
        assertEquals(slot(1, 2), TurnaroundBuffer.none().blockingSlot(slot(1, 2)));
        assertThrows(IllegalArgumentException.class, () -> new TurnaroundBuffer(Duration.ofNanos(-1)));
        assertThrows(NullPointerException.class, () -> new TurnaroundBuffer(null));
        assertThrows(NullPointerException.class, () -> new BookingService(ROOMS, new MutableClock(), new FirstByCode(),
                null));
    }

    @Test
    void bufferOverflowDuringSearchBookingAndMovePreservesPublication() {
        var service = buffered();
        var booking = service.book(ALA, people(20), slot(1, 2), "A");
        var before = service.snapshot();
        var extreme = new TimeSlot(Instant.MAX.minusSeconds(3600), Instant.MAX);
        assertThrows(DateTimeException.class, () -> service.analyzeAvailability(people(20), extreme));
        assertThrows(DateTimeException.class, () -> service.book(ALA, people(20), extreme, "B"));
        assertThrows(DateTimeException.class, () -> service.reschedule(booking.id(), ALA, extreme));
        assertSame(before, service.snapshot());
        var huge = new TurnaroundBuffer(Duration.ofSeconds(Long.MAX_VALUE));
        assertThrows(ArithmeticException.class, () -> huge.blockingSlot(slot(1, 2)));
    }

    @Test
    void nullMoveArgumentsAreProgrammingErrorsWithoutPublication() {
        var service = service(new MutableClock());
        var original = service.book(ALA, people(20), slot(1, 2), "A");
        var before = service.snapshot();
        assertThrows(NullPointerException.class, () -> service.reschedule(null, ALA, slot(2, 3)));
        assertThrows(NullPointerException.class, () -> service.reschedule(original.id(), null, slot(2, 3)));
        assertThrows(NullPointerException.class, () -> service.reschedule(original.id(), ALA, null));
        assertSame(before, service.snapshot());
    }

    private static BookingService buffered() {
        return new BookingService(ROOMS, new MutableClock(), new SmallestCapacity(),
                new TurnaroundBuffer(Duration.ofMinutes(15)));
    }

    private static void reject(BookingService service, BookingException.Reason reason, Runnable action) {
        var before = service.snapshot();
        assertEquals(reason, assertThrows(BookingException.class, action::run).reason());
        assertSame(before, service.snapshot());
    }
}
