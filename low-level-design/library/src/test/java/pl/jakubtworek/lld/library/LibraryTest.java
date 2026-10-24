package pl.jakubtworek.lld.library;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static pl.jakubtworek.lld.library.Fixtures.*;
import static pl.jakubtworek.lld.library.LibraryException.Reason.*;

/**
 * Scenariusze sprawdzają efekt publicznej operacji, nie prywatny układ map.
 * Odmowa wymaga także zachowania tożsamości snapshotu. Zwrócony Loan nie
 * wystarcza do wykazania, że egzemplarz został zajęty, a kolejka skrócona.
 */
class LibraryTest {

    @Test
    void copiesAreIndependentAndReturnFreesExactlyOneResource() {
        var library = library(2);
        var initial = library.snapshot();
        Loan first = library.borrow(M1, B1);
        var oneBorrowed = library.snapshot();
        Loan second = library.borrow(M2, B1);
        assertEquals(new CopyId("C1"), first.copyId());
        assertEquals(new CopyId("C2"), second.copyId());
        reject(library, NO_COPY_AVAILABLE, () -> library.borrow(M3, B1));
        assertSame(first, library.returnCopy(M1, first.copyId(), first.id()));
        assertEquals(List.of(new BookCopy(first.copyId(), B1)), library.snapshot().availableCopies(B1));
        assertEquals(List.of(second), library.snapshot().activeLoans(M2));
        assertEquals(2, initial.availableCopies(B1).size());
        assertEquals(1, oneBorrowed.availableCopies(B1).size());
        assertEquals(List.of(first), oneBorrowed.activeLoans(M1));
    }

    @Test
    void sameReaderCannotBorrowOrReserveAnotherCopyOfOwnedTitle() {
        var library = library(2);
        library.borrow(M1, B1);
        reject(library, ALREADY_BORROWED_TITLE, () -> library.borrow(M1, B1));
        reject(library, ALREADY_BORROWED_TITLE, () -> library.reserve(M1, B1));
        assertEquals(1, library.snapshot().availableCopies(B1).size());
    }

    @ParameterizedTest
    @EnumSource(Membership.class)
    void membershipControlsLimitAndReturnRestoresCapacity(Membership membership) {
        var library = library(1);
        MemberId reader = membership == Membership.STANDARD ? M1 : M3;
        int expectedLimit = membership == Membership.STANDARD ? 2 : 5;
        for (int i = 1; i <= expectedLimit; i++) {
            library.borrow(reader, new BookId("B" + i));
        }
        BookId next = new BookId("B" + (expectedLimit + 1));
        reject(library, LOAN_LIMIT_REACHED, () -> library.borrow(reader, next));
        library.returnCopy(reader, new CopyId("C1"), library.snapshot().loans().get(new CopyId("C1")).id());
        library.borrow(reader, next);
        assertEquals(expectedLimit, library.snapshot().activeLoans(reader).size());
    }

    @ParameterizedTest
    @EnumSource(Membership.class)
    void dueDateUsesOneClockReadAndExactDuration(Membership membership) {
        var reads = new int[1];
        var clock = new SuppliedClock(() -> NOW.plusSeconds(reads[0]++), ZoneOffset.UTC);
        var library = library(1, clock);
        Loan loan = library.borrow(membership == Membership.STANDARD ? M1 : M3, B1);
        assertEquals(1, reads[0]);
        assertEquals(NOW, loan.borrowedAt());
        assertEquals(NOW.plus(Duration.ofDays(membership == Membership.STANDARD ? 14 : 28)), loan.dueAt());
        assertFalse(loan.isPastDueAt(loan.dueAt()));
        assertTrue(loan.isPastDueAt(loan.dueAt().plusNanos(1)));
    }

    @Test
    void firstReservationHasPriorityWhenOnlyOneCopyIsFree() {
        var library = library(1);
        Loan original = library.borrow(M4, B1);
        library.reserve(M1, B1);
        library.reserve(M2, B1);
        var queued = library.snapshot();
        library.returnCopy(M4, original.copyId(), original.id());
        assertTrue(library.snapshot().loans().isEmpty(), "Zwrot nie wypożycza książki automatycznie");
        reject(library, RESERVATION_PRIORITY, () -> library.borrow(M2, B1));
        reject(library, RESERVATION_PRIORITY, () -> library.borrow(M3, B1));
        Loan first = library.borrow(M1, B1);
        assertEquals(List.of(M2), library.snapshot().queueFor(B1));
        library.returnCopy(M1, first.copyId(), first.id());
        library.borrow(M2, B1);
        assertFalse(library.snapshot().reservations().containsKey(B1));
        assertEquals(List.of(M1, M2), queued.queueFor(B1));
    }

    @Test
    void twoFreeCopiesAllowSecondQueuedReaderToCollectFirst() {
        var library = library(2);
        library.reserve(M1, B1);
        library.reserve(M2, B1);
        library.reserve(M3, B1);
        reject(library, RESERVATION_PRIORITY, () -> library.borrow(M3, B1));
        reject(library, RESERVATION_PRIORITY, () -> library.borrow(M4, B1));
        library.borrow(M2, B1);
        assertEquals(List.of(M1, M3), library.snapshot().queueFor(B1));
        reject(library, RESERVATION_PRIORITY, () -> library.borrow(M3, B1));
        library.borrow(M1, B1);
        assertEquals(List.of(M3), library.snapshot().queueFor(B1));
    }

    @Test
    void walkInCanBorrowOnlySurplusBeyondReservations() {
        var library = library(2);
        library.reserve(M1, B1);
        library.borrow(M2, B1);
        assertEquals(List.of(M1), library.snapshot().queueFor(B1));
        reject(library, RESERVATION_PRIORITY, () -> library.borrow(M3, B1));
        library.borrow(M1, B1);
        assertEquals(2, library.snapshot().loans().size());
    }

    @Test
    void readerAtLimitKeepsPriorityUntilCancellation() {
        var library = library(1);
        library.borrow(M1, B2);
        library.borrow(M1, B3);
        // Czekanie nie zużywa limitu wypożyczeń.
        library.reserve(M1, B1);
        library.reserve(M2, B1);
        reject(library, LOAN_LIMIT_REACHED, () -> library.borrow(M1, B1));
        reject(library, RESERVATION_PRIORITY, () -> library.borrow(M2, B1));
        assertEquals(List.of(M1, M2), library.snapshot().queueFor(B1));
        library.cancelReservation(M1, B1);
        library.borrow(M2, B1);
    }

    @Test
    void blockedFirstReaderDoesNotBlockSecondEntitledCopy() {
        var library = library(2);
        library.borrow(M1, B2);
        library.borrow(M1, B3);
        library.reserve(M1, B1);
        library.reserve(M2, B1);
        library.borrow(M2, B1);
        assertEquals(List.of(M1), library.snapshot().queueFor(B1));
        assertEquals(1, library.snapshot().availableCopies(B1).size());
    }

    @Test
    void cancellingMiddleEntryPreservesOtherPositionsAndAllowsRejoiningAtEnd() {
        var library = library(1);
        library.reserve(M1, B1);
        library.reserve(M2, B1);
        library.reserve(M3, B1);
        var previous = library.snapshot();
        library.cancelReservation(M2, B1);
        assertEquals(List.of(M1, M3), library.snapshot().queueFor(B1));
        library.reserve(M2, B1);
        assertEquals(List.of(M1, M3, M2), library.snapshot().queueFor(B1));
        assertEquals(List.of(M1, M2, M3), previous.queueFor(B1));
        reject(library, ALREADY_RESERVED, () -> library.reserve(M2, B1));
        reject(library, NO_RESERVATION, () -> library.cancelReservation(M4, B1));
    }

    @Test
    void returnRejectsWrongOwnerAndRepeatedReturn() {
        var library = library(1);
        Loan loan = library.borrow(M1, B1);
        reject(library, NOT_BORROWER, () -> library.returnCopy(M2, loan.copyId(), loan.id()));
        library.returnCopy(M1, loan.copyId(), loan.id());
        reject(library, COPY_NOT_ON_LOAN, () -> library.returnCopy(M1, loan.copyId(), loan.id()));
    }

    @Test
    void unknownReferencesAndNullArgumentsDoNotPublishState() {
        var library = library(1);
        var absent = new LoanId(java.util.UUID.randomUUID());
        reject(library, UNKNOWN_MEMBER, () -> library.borrow(new MemberId("X"), B1));
        reject(library, UNKNOWN_BOOK, () -> library.borrow(M1, new BookId("X")));
        reject(library, UNKNOWN_MEMBER, () -> library.reserve(new MemberId("X"), B1));
        reject(library, UNKNOWN_BOOK, () -> library.reserve(M1, new BookId("X")));
        reject(library, UNKNOWN_MEMBER, () -> library.cancelReservation(new MemberId("X"), B1));
        reject(library, UNKNOWN_BOOK, () -> library.cancelReservation(M1, new BookId("X")));
        reject(library, UNKNOWN_MEMBER, () -> library.returnCopy(new MemberId("X"), new CopyId("C1"), absent));
        reject(library, UNKNOWN_COPY, () -> library.returnCopy(M1, new CopyId("X"), absent));
        var before = library.snapshot();
        List<Runnable> invalid = List.of(() -> library.borrow(null, B1), () -> library.borrow(M1, null),
                () -> library.reserve(null, B1), () -> library.reserve(M1, null), () -> library.cancelReservation(null,
                B1), () -> library.cancelReservation(M1, null), () -> library.returnCopy(null, new CopyId("C1"),
                absent), () -> library.returnCopy(M1, null, absent), () -> library.returnCopy(M1, new CopyId("C1"),
                null));
        for (Runnable action : invalid) {
            assertThrows(NullPointerException.class, action::run);
            assertSame(before, library.snapshot());
        }
    }

    @Test
    void clockFailureAndDeadlineOverflowKeepReservationAndCopyUntouched() {
        var failing = Clock.offset(Clock.fixed(Instant.MAX, ZoneOffset.UTC), Duration.ofSeconds(1));
        for (Clock clock : List.of(failing, Clock.fixed(Instant.MAX, ZoneOffset.UTC))) {
            var library = library(1, clock);
            library.reserve(M1, B1);
            var before = library.snapshot();
            assertThrows(DateTimeException.class, () -> library.borrow(M1, B1));
            assertSame(before, library.snapshot());
            assertEquals(List.of(M1), library.snapshot().queueFor(B1));
            assertEquals(1, library.snapshot().availableCopies(B1).size());
            // Anulowanie nie zależy od Clock.
            library.cancelReservation(M1, B1);
        }
    }

    @Test
    void returnDoesNotDependOnWorkingClock() {
        var broken = new boolean[1];
        var library = library(1, new SuppliedClock(() -> {
            if (broken[0]) {
                throw new IllegalStateException("Awaria zegara");
            }
            return NOW;
        }, ZoneOffset.UTC));
        Loan loan = library.borrow(M1, B1);
        broken[0] = true;
        assertDoesNotThrow(() -> library.returnCopy(M1, loan.copyId(), loan.id()));
        assertTrue(library.snapshot().loans().isEmpty());
    }

    @Test
    void ownershipIncludesNestedQueuesAndSourceCollections() {
        var books = new ArrayList<>(List.of(new Book(B1, "T", "A")));
        var copies = new ArrayList<>(List.of(new BookCopy(new CopyId("C1"), B1)));
        var members = new ArrayList<>(List.of(new Member(M1, "A", Membership.STANDARD)));
        var first = new Library(books, copies, members, CLOCK);
        var second = new Library(books, copies, members, CLOCK);
        books.clear();
        copies.clear();
        members.clear();
        first.reserve(M1, B1);
        var old = first.snapshot();
        assertThrows(UnsupportedOperationException.class, () -> old.books().clear());
        assertThrows(UnsupportedOperationException.class, () -> old.copies().clear());
        assertThrows(UnsupportedOperationException.class, () -> old.members().clear());
        assertThrows(UnsupportedOperationException.class, () -> old.loans().clear());
        assertThrows(UnsupportedOperationException.class, () -> old.reservations().clear());
        assertThrows(UnsupportedOperationException.class, () -> old.queueFor(B1).clear());
        assertThrows(UnsupportedOperationException.class, () -> old.availableCopies(B1).clear());
        first.borrow(M1, B1);
        assertEquals(List.of(M1), old.queueFor(B1));
        assertTrue(old.loans().isEmpty());
        assertTrue(second.snapshot().queueFor(B1).isEmpty());
        assertEquals(1, second.snapshot().availableCopies(B1).size());
    }

    @Test
    void titlesWithoutCopiesAndEmptyCatalogAreValidButNotBorrowable() {
        var library = library(0);
        library.reserve(M1, B1);
        reject(library, NO_COPY_AVAILABLE, () -> library.borrow(M1, B1));
        assertTrue(new Library(List.of(), List.of(), List.of(), CLOCK).snapshot().books().isEmpty());
    }

    private static void reject(Library library, LibraryException.Reason reason, Runnable action) {
        var before = library.snapshot();
        assertEquals(reason, assertThrows(LibraryException.class, action::run).reason());
        assertSame(before, library.snapshot(), "Odmowa nie może zmienić publikacji");
    }

    /**
     * Strefowe widoki współdzielą dostawcę czasu; test może włączyć awarię po borrow.
     */
    private static final class SuppliedClock extends Clock {

        private final Supplier<Instant> source;
        private final ZoneId zone;

        SuppliedClock(Supplier<Instant> source, ZoneId zone) {
            this.source = source;
            this.zone = zone;
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId newZone) {
            return new SuppliedClock(source, newZone);
        }

        @Override
        public Instant instant() {
            return source.get();
        }
    }
}
