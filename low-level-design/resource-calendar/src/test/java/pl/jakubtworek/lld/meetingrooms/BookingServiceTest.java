package pl.jakubtworek.lld.meetingrooms;

import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static pl.jakubtworek.lld.meetingrooms.Fixtures.*;
import static pl.jakubtworek.lld.meetingrooms.BookingException.Reason.*;

/**
 * Scenariusz obejmuje wynik i kalendarz; po odmowie sprawdzamy także tożsamość poprzedniej publikacji.
 */
class BookingServiceTest {

    @Test
    void bookConflictCancelAndReusePreserveHistoricalView() {
        var service = service(new MutableClock());
        var initial = service.snapshot();
        var booking = service.book(ALA, people(20), slot(1, 2), " Planowanie ");
        var booked = service.snapshot();
        assertEquals(BIG.id(), booking.roomId());
        assertEquals("Planowanie", booking.subject());
        reject(service, NO_AVAILABLE_ROOM, () -> service.book(BARTEK, people(20), slot(1, 2), "B"));
        var cancelled = service.cancel(booking.id(), ALA);
        assertEquals(BookingStatus.CANCELLED, cancelled.status());
        assertEquals(booking.id(), cancelled.id());
        var replacement = service.book(BARTEK, people(20), slot(1, 2), "B");
        assertNotEquals(booking.id(), replacement.id());
        assertEquals(2, service.snapshot().bookings().size());
        assertTrue(initial.bookings().isEmpty());
        assertEquals(BookingStatus.CONFIRMED, booked.bookings().get(booking.id()).status());
        // Stare ID nie anuluje nowej rezerwacji tego samego zasobu i terminu.
        reject(service, NOT_CONFIRMED, () -> service.cancel(booking.id(), ALA));
        assertEquals(BookingStatus.CONFIRMED, service.snapshot().bookings().get(replacement.id()).status());
    }

    @Test
    void containingContainedAndPartialOverlapsRejectWhileBothAdjacenciesWork() {
        var service = service(new MutableClock());
        service.book(ALA, people(20), slot(2, 4), "A");
        for (TimeSlot collision : List.of(slot(1, 5), slot(3, 4), slot(1, 3), slot(3, 5), slot(2, 4))) {
            reject(service, NO_AVAILABLE_ROOM, () -> service.book(BARTEK, people(20), collision, "B"));
        }
        service.book(ALA, people(20), slot(1, 2), "Przed");
        service.book(ALA, people(20), slot(4, 5), "Po");
        assertEquals(3, service.snapshot().bookings().size());
    }

    @Test
    void equipmentCapacityAndBusyRoomsFilterBeforeSelection() {
        var service = service(new MutableClock());
        var required = new RoomRequirements(3, Set.of(Equipment.PROJECTOR, Equipment.WHITEBOARD));
        assertEquals(List.of(BIG, MEDIUM), service.availableRooms(required, slot(1, 2)));
        assertEquals(MEDIUM.id(), service.book(ALA, required, slot(1, 2), "A").roomId());
        assertEquals(BIG.id(), service.book(ALA, required, slot(1, 2), "B").roomId());
        assertTrue(service.availableRooms(required, slot(1, 2)).isEmpty());
        reject(service, NO_AVAILABLE_ROOM, () -> service.book(ALA, required, slot(1, 2), "C"));
        reject(service, NO_AVAILABLE_ROOM, () -> service.book(ALA, new RoomRequirements(1,
                Set.of(Equipment.VIDEO_CONFERENCE)), slot(2, 3), "C"));
    }

    @Test
    void strategiesSelectDifferentRoomsAndResolveCapacityTiesByCode() {
        var clock = new MutableClock();
        assertEquals(BIG.id(), new BookingService(ROOMS, clock, new FirstByCode()).book(ALA, people(3), slot(1, 2), "A")
                .roomId());
        assertEquals(SMALL.id(), service(clock).book(ALA, people(3), slot(1, 2), "A").roomId());
        var tied = new MeetingRoom(new RoomId("B0"), "Remis", 4, Set.of());
        var service = new BookingService(List.of(SMALL, tied), clock, new SmallestCapacity());
        assertEquals(tied.id(), service.book(ALA, people(3), slot(1, 2), "A").roomId());
    }

    @Test
    void searchIsReadOnlyAndDoesNotPromiseRoomWillRemainAvailable() {
        var service = service(new MutableClock());
        var before = service.snapshot();
        var found = service.availableRooms(people(20), slot(1, 2));
        assertSame(before, service.snapshot());
        assertThrows(UnsupportedOperationException.class, found::clear);
        service.book(ALA, people(20), slot(1, 2), "A");
        assertEquals(List.of(BIG), found);
        reject(service, NO_AVAILABLE_ROOM, () -> service.book(BARTEK, people(20), slot(1, 2), "B"));
    }

    @Test
    void startNowCanBeBookedButCannotBeCancelledAndPastStartsReject() {
        var clock = new MutableClock();
        var service = service(clock);
        var booking = service.book(ALA, people(1), slot(0, 1), "Teraz");
        reject(service, ALREADY_STARTED, () -> service.cancel(booking.id(), ALA));
        reject(service, PAST_START, () -> service.book(ALA, people(1), new TimeSlot(NOW.minusNanos(1),
                NOW.plusSeconds(1)), "Wczoraj"));
        reject(service, PAST_START, () -> service.availableRooms(people(1), slot(-1, 1)));
        clock.at(NOW.plusSeconds(3600));
        assertEquals(BookingStatus.CONFIRMED, service.snapshot().bookings().get(booking.id()).status());
        assertEquals(3, service.availableRooms(people(1), slot(1, 2)).size());
    }

    @Test
    void cancellationRequiresOrganizerConfirmedStatusAndStrictlyFutureStart() {
        var clock = new MutableClock();
        var service = service(clock);
        var booking = service.book(ALA, people(20), slot(1, 2), "A");
        reject(service, NOT_ORGANIZER, () -> service.cancel(booking.id(), BARTEK));
        reject(service, UNKNOWN_BOOKING, () -> service.cancel(new BookingId(UUID.randomUUID()), ALA));
        clock.at(slot(1, 2).start().minusNanos(1));
        service.cancel(booking.id(), ALA);
        reject(service, NOT_CONFIRMED, () -> service.cancel(booking.id(), ALA));
        var next = service.book(ALA, people(20), slot(1, 2), "B");
        clock.at(slot(1, 2).start());
        reject(service, ALREADY_STARTED, () -> service.cancel(next.id(), ALA));
        clock.at(slot(1, 2).end());
        reject(service, ALREADY_STARTED, () -> service.cancel(next.id(), ALA));
    }

    @Test
    void malformedFailingAndMutatingStrategiesCannotPublish() {
        List<RoomSelectionPolicy> invalid = List.of(rooms -> null, rooms -> SMALL, rooms -> new MeetingRoom(BIG.id(),
                "Podmieniona", 100, Set.of()), rooms -> {
            throw new IllegalStateException("Awaria strategii");
        }, rooms -> {
            rooms.clear();
            return BIG;
        });
        for (var policy : invalid) {
            var service = new BookingService(List.of(BIG), new MutableClock(), policy);
            var before = service.snapshot();
            assertThrows(RuntimeException.class, () -> service.book(ALA, people(1), slot(1, 2), "A"));
            assertSame(before, service.snapshot());
        }
    }

    @Test
    void inputAndSnapshotCollectionsCannotMutateCalendar() {
        var rooms = new ArrayList<>(ROOMS);
        var service = new BookingService(rooms, new MutableClock(), new FirstByCode());
        rooms.clear();
        var view = service.snapshot();
        assertEquals(3, view.rooms().size());
        assertThrows(UnsupportedOperationException.class, () -> view.rooms().clear());
        assertThrows(UnsupportedOperationException.class, () -> view.bookings().clear());
    }

    @Test
    void invalidCatalogArgumentsAndSubjectCannotPublish() {
        var clock = new MutableClock();
        assertThrows(IllegalArgumentException.class, () -> new BookingService(List.of(), clock, new FirstByCode()));
        assertThrows(IllegalArgumentException.class, () -> new BookingService(List.of(BIG, BIG), clock,
                new FirstByCode()));
        assertThrows(NullPointerException.class, () -> new BookingService(ROOMS, null, new FirstByCode()));
        assertThrows(NullPointerException.class, () -> new BookingService(ROOMS, clock, null));
        var service = service(clock);
        var before = service.snapshot();
        assertThrows(IllegalArgumentException.class, () -> service.book(ALA, people(1), slot(1, 2), " "));
        List<Runnable> nulls = List.of(() -> service.book(null, people(1), slot(1, 2), "A"), () -> service.book(ALA,
                null, slot(1, 2), "A"), () -> service.book(ALA, people(1), null, "A"), () -> service.book(ALA,
                people(1), slot(1, 2), null), () -> service.cancel(null, ALA),
                () -> service.cancel(new BookingId(UUID.randomUUID()), null));
        for (var action : nulls) {
            assertThrows(NullPointerException.class, action::run);
            assertSame(before, service.snapshot());
        }
    }

    @Test
    void bookingReadsClockExactlyOnceAndUsesInstantRegardlessOfZone() {
        var reads = new AtomicInteger();
        Clock ticking = new Clock() {

            @Override
            public java.time.ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Instant instant() {
                return NOW.plusSeconds(reads.getAndIncrement() * 3600L);
            }
        };
        var service = service(ticking);
        service.book(ALA, people(1), slot(0, 1), "A");
        assertEquals(1, reads.get());
        var shifted = service(Clock.fixed(NOW, ZoneOffset.ofHours(5)));
        assertEquals(SMALL.id(), shifted.book(ALA, people(1), slot(0, 1), "A").roomId());
    }

    private static void reject(BookingService service, BookingException.Reason reason, Runnable action) {
        var before = service.snapshot();
        assertEquals(reason, assertThrows(BookingException.class, action::run).reason());
        assertSame(before, service.snapshot());
    }
}
