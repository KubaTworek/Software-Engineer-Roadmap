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
import java.util.Collections;
import java.util.List;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;
import static pl.jakubtworek.lld.library.Fixtures.*;
import static pl.jakubtworek.lld.library.LibraryException.Reason.*;

/**
 * Testujemy tożsamość relacji, nie tylko parę czytelnik–kopia. Oddzielne
 * przypadki czasu chronią granice niemożliwe do trafienia zwykłym losowaniem.
 */
class LoanLifecycleTest {

    @Test
    void staleReturnAndRenewalCannotModifyNewLoanOfSameCopyToSameReader() {
        var library = library(1);
        var old = library.borrow(M1, B1);
        library.returnCopy(M1, old.copyId(), old.id());
        var current = library.borrow(M1, B1);
        assertNotEquals(old.id(), current.id());
        reject(library, STALE_LOAN, () -> library.returnCopy(M1, old.copyId(), old.id()));
        reject(library, STALE_LOAN, () -> library.renew(M1, old.copyId(), old.id()));
        assertSame(current, library.snapshot().loans().get(current.copyId()));
        library.returnCopy(M1, current.copyId(), current.id());
    }

    @Test
    void identifierFromAnotherLibraryCannotCloseMatchingCopyAndReader() {
        var first = library(1);
        var second = library(1);
        var foreign = first.borrow(M1, B1);
        var local = second.borrow(M1, B1);
        reject(second, STALE_LOAN, () -> second.returnCopy(M1, local.copyId(), foreign.id()));
    }

    @ParameterizedTest
    @EnumSource(Membership.class)
    void renewalExtendsOriginalDeadlineOnceAndKeepsIdentityAndOccupancy(Membership membership) {
        var clock = new TestClock(NOW);
        var library = library(1, clock);
        var member = membership == Membership.STANDARD ? M1 : M3;
        var original = library.borrow(member, B1);
        var previous = library.snapshot();
        clock.now = NOW.plus(Duration.ofDays(1));
        int readsBefore = clock.reads;
        var renewed = library.renew(member, original.copyId(), original.id());
        var period = Duration.ofDays(membership == Membership.STANDARD ? 14 : 28);
        assertEquals(readsBefore + 1, clock.reads);
        assertEquals(original.dueAt().plus(period), renewed.dueAt());
        assertEquals(original.borrowedAt(), renewed.borrowedAt());
        assertEquals(original.id(), renewed.id());
        assertTrue(renewed.renewed());
        assertFalse(original.renewed());
        assertSame(original, previous.loans().get(original.copyId()));
        assertEquals(1, library.snapshot().activeLoans(member).size());
        assertEquals(0, library.snapshot().availableCopies(B1).size());
        reject(library, ALREADY_RENEWED, () -> library.renew(member, renewed.copyId(), renewed.id()));
        // Token sprzed przedłużenia nadal dotyczy tej samej relacji i pozwala
        // ją zwrócić. LoanId nie jest numerem wersji każdej zmiany danych.
        assertSame(renewed, library.returnCopy(member, original.copyId(), original.id()));
    }

    @Test
    void renewalAtDeadlineSucceedsButOneNanosecondLaterFails() {
        for (int nanos : new int[] { 0, 1 }) {
            var clock = new TestClock(NOW);
            var library = library(1, clock);
            var loan = library.borrow(M1, B1);
            clock.now = loan.dueAt().plusNanos(nanos);
            if (nanos == 0) {
                assertTrue(library.renew(M1, loan.copyId(), loan.id()).renewed());
            } else {
                reject(library, LOAN_OVERDUE, () -> library.renew(M1, loan.copyId(), loan.id()));
            }
        }
    }

    @Test
    void waitingReaderBlocksRenewalEvenWithSpareCopyAndCancellationUnblocksIt() {
        var library = library(2);
        var loan = library.borrow(M1, B1);
        library.reserve(M2, B1);
        reject(library, WAITING_RESERVATIONS, () -> library.renew(M1, loan.copyId(), loan.id()));
        assertEquals(List.of(M2), library.snapshot().queueFor(B1));
        library.cancelReservation(M2, B1);
        assertTrue(library.renew(M1, loan.copyId(), loan.id()).renewed());
    }

    @Test
    void reservationForAnotherTitleDoesNotBlockRenewal() {
        var library = library(1);
        var loan = library.borrow(M1, B1);
        library.reserve(M2, B2);
        assertTrue(library.renew(M1, loan.copyId(), loan.id()).renewed());
        assertEquals(List.of(M2), library.snapshot().queueFor(B2));
    }

    @Test
    void failedClockReversedTimeAndOverflowKeepLoanAndRenewalAllowance() {
        // Pierwszy termin mieści się w Instant; drugi po dodaniu 14 dób już nie.
        for (int variant = 0; variant < 3; variant++) {
            var start = variant == 2 ? Instant.MAX.minus(Duration.ofDays(20)) : NOW;
            var clock = new TestClock(start);
            var library = library(1, clock);
            var loan = library.borrow(M1, B1);
            var before = library.snapshot();
            if (variant == 0) {
                clock.failure = new IllegalStateException("Awaria zegara");
            }
            if (variant == 1) {
                clock.now = NOW.minusSeconds(1);
            }
            if (variant == 2) {
                assertThrows(DateTimeException.class, () -> library.renew(M1, loan.copyId(), loan.id()));
            } else {
                assertThrows(IllegalStateException.class, () -> library.renew(M1, loan.copyId(), loan.id()));
            }
            assertSame(before, library.snapshot());
            assertFalse(library.snapshot().loans().get(loan.copyId()).renewed());
            if (variant != 2) {
                clock.failure = null;
                clock.now = NOW;
                assertTrue(library.renew(M1, loan.copyId(), loan.id()).renewed());
            } else {
                // Przepełnienie reprezentacji nie blokuje bezpiecznego zwrotu.
                library.returnCopy(M1, loan.copyId(), loan.id());
            }
        }
    }

    @Test
    void renewalChecksReferencesOwnershipAndNullArgumentsBeforePublication() {
        var library = library(1);
        var loan = library.borrow(M1, B1);
        reject(library, UNKNOWN_MEMBER, () -> library.renew(new MemberId("X"), loan.copyId(), loan.id()));
        reject(library, UNKNOWN_COPY, () -> library.renew(M1, new CopyId("X"), loan.id()));
        reject(library, COPY_NOT_ON_LOAN, () -> library.renew(M1, new CopyId("C3"), loan.id()));
        reject(library, NOT_BORROWER, () -> library.renew(M2, loan.copyId(), loan.id()));
        var before = library.snapshot();
        assertThrows(NullPointerException.class, () -> library.renew(null, loan.copyId(), loan.id()));
        assertThrows(NullPointerException.class, () -> library.renew(M1, null, loan.id()));
        assertThrows(NullPointerException.class, () -> library.renew(M1, loan.copyId(), null));
        assertSame(before, library.snapshot());
    }

    @Test
    void copyChoiceAndPriorityAreIndependentOfCatalogInsertionOrder() {
        // Permutacje są próbką porządków, nie benchmarkiem. C10<C2 jest
        // celowo leksykograficzne; skan minimum ma zachować poprzedni kontrakt.
        var random = new Random(213);
        for (int sample = 0; sample < 40; sample++) {
            var copies = new ArrayList<>(List.of(new BookCopy(new CopyId("C2"), B1), new BookCopy(new CopyId("C10"),
                    B1), new BookCopy(new CopyId("C3"), B2)));
            Collections.shuffle(copies, random);
            var library = new Library(List.of(new Book(B2, "B", "A"), new Book(B1, "A", "A")), copies,
                    List.of(new Member(M2, "B", Membership.STANDARD), new Member(M1, "A", Membership.STANDARD)), CLOCK);
            library.reserve(M1, B1);
            // Nadwyżka ponad jedną rezerwację.
            var first = library.borrow(M2, B1);
            assertEquals(new CopyId("C10"), first.copyId());
            assertEquals(List.of(new BookCopy(new CopyId("C2"), B1)), library.snapshot().availableCopies(B1));
            assertEquals(new CopyId("C2"), library.borrow(M1, B1).copyId());
        }
    }

    private static void reject(Library library, LibraryException.Reason reason, Runnable action) {
        var before = library.snapshot();
        assertEquals(reason, assertThrows(LibraryException.class, action::run).reason());
        assertSame(before, library.snapshot());
    }

    /**
     * Test steruje źródłem, a strefowe widoki dzielą odczyty i stan awarii.
     */
    private static final class TestClock extends Clock {

        Instant now;
        RuntimeException failure;
        int reads;

        TestClock(Instant now) {
            this.now = now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            java.util.Objects.requireNonNull(zone, "zone");
            return new Clock() {

                @Override
                public ZoneId getZone() {
                    return zone;
                }

                @Override
                public Clock withZone(ZoneId next) {
                    return TestClock.this.withZone(next);
                }

                @Override
                public Instant instant() {
                    return TestClock.this.instant();
                }
            };
        }

        @Override
        public Instant instant() {
            reads++;
            if (failure != null) {
                throw failure;
            }
            return now;
        }
    }
}
