package pl.jakubtworek.lld.library;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;
import static pl.jakubtworek.lld.library.Fixtures.CLOCK;
import static pl.jakubtworek.lld.library.LibraryException.Reason.*;

/**
 * Niezależny model ma tablicę właścicieli egzemplarzy i kolejki liczb. Generator
 * wykonuje 1200 poleceń, także odmów, i ponownie sprawdza stare widoki.
 * To deterministyczna próbka historii, nie dowód dla wszystkich katalogów.
 */
class GeneratedHistoryTest {

    private static final int[] BOOK_BY_COPY = { 0, 0, 1, 2 };
    private static final int[] LIMITS = { 2, 2, 5 };

    private static final class Model {

        final int[] owner = { -1, -1, -1, -1 };
        final LoanId[] ids = new LoanId[4];
        final boolean[] renewed = new boolean[4];
        final List<List<Integer>> queues = List.of(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }

    // Archiwum kopiuje wnętrza, a nie tylko referencję do tablicy/list modelu.
    private record Archive(LibrarySnapshot view, int[] owner, LoanId[] ids, boolean[] renewed,
            List<List<Integer>> queues) {
    }

    @ParameterizedTest
    @ValueSource(longs = { 17, 213, 901 })
    void generatedCommandsPreserveOwnershipPriorityLimitsAndOldViews(long seed) {
        var books = List.of(new Book(book(0), "A", "X"), new Book(book(1), "B", "X"), new Book(book(2), "C", "X"));
        var copies = new ArrayList<BookCopy>();
        for (int i = 0; i < 4; i++) {
            copies.add(new BookCopy(copy(i), book(BOOK_BY_COPY[i])));
        }
        var members = List.of(new Member(member(0), "A", Membership.STANDARD), new Member(member(1), "B",
                Membership.STANDARD), new Member(member(2), "C", Membership.EXTENDED));
        var library = new Library(books, copies, members, CLOCK);
        var model = new Model();
        var random = new Random(seed);
        var archive = new ArrayList<Archive>();
        for (int step = 0; step < 400; step++) {
            int command = random.nextInt(5);
            // 3 jest celowo nieznanym czytelnikiem.
            int reader = random.nextInt(4);
            int target = random.nextInt(command >= 3 ? 5 : 4);
            var reason = expected(model, command, reader, target);
            var before = library.snapshot();
            Runnable action = switch(command) {
                case 0 ->
                    () -> library.borrow(member(reader), book(target));
                case 1 ->
                    () -> library.reserve(member(reader), book(target));
                case 2 ->
                    () -> library.cancelReservation(member(reader), book(target));
                case 3 ->
                    () -> library.returnCopy(member(reader), copy(target), target < 4
                            && model.ids[target] != null ? model.ids[target] : new LoanId(new java.util.UUID(0, 0)));
                case 4 ->
                    () -> library.renew(member(reader), copy(target), target < 4
                            && model.ids[target] != null ? model.ids[target] : new LoanId(new java.util.UUID(0, 0)));
                default ->
                    throw new AssertionError();
            };
            if (reason != null) {
                assertEquals(reason, assertThrows(LibraryException.class, action::run).reason(), "krok=" + step);
                assertSame(before, library.snapshot());
            } else {
                action.run();
                switch(command) {
                    case 0 ->
                        {
                            int chosen = -1;
                            for (int i = 0; i < 4; i++) {
                                if (BOOK_BY_COPY[i] == target && model.owner[i] < 0) {
                                    chosen = i;
                                    break;
                                }
                            }
                            assertTrue(chosen >= 0);
                            model.owner[chosen] = reader;
                            model.ids[chosen] = library.snapshot().loans().get(copy(chosen)).id();
                            model.renewed[chosen] = false;
                            model.queues.get(target).remove(Integer.valueOf(reader));
                        }
                    case 1 ->
                        model.queues.get(target).add(reader);
                    case 2 ->
                        model.queues.get(target).remove(Integer.valueOf(reader));
                    case 3 ->
                        {
                            model.owner[target] = -1;
                            model.ids[target] = null;
                            model.renewed[target] = false;
                        }
                    case 4 ->
                        model.renewed[target] = true;
                    default ->
                        throw new AssertionError();
                }
            }
            assertView(library.snapshot(), model.owner, model.ids, model.renewed, model.queues);
            archive.add(new Archive(library.snapshot(), model.owner.clone(), model.ids.clone(), model.renewed.clone(),
                    model.queues.stream().map(List::copyOf).toList()));
        }
        for (Archive old : archive) {
            assertView(old.view(), old.owner(), old.ids(), old.renewed(), old.queues());
        }
    }

    private static LibraryException.Reason expected(Model model, int command, int reader, int target) {
        if (reader == 3) {
            return UNKNOWN_MEMBER;
        }
        if (command >= 3) {
            if (target == 4) {
                return UNKNOWN_COPY;
            }
            if (model.owner[target] < 0) {
                return COPY_NOT_ON_LOAN;
            }
            if (model.owner[target] != reader) {
                return NOT_BORROWER;
            }
            if (command == 4) {
                if (!model.queues.get(BOOK_BY_COPY[target]).isEmpty()) {
                    return WAITING_RESERVATIONS;
                }
                if (model.renewed[target]) {
                    return ALREADY_RENEWED;
                }
            }
            return null;
        }
        if (target == 3) {
            return UNKNOWN_BOOK;
        }
        List<Integer> queue = model.queues.get(target);
        if (command == 2) {
            return queue.contains(reader) ? null : NO_RESERVATION;
        }
        int owned = 0;
        int free = 0;
        for (int i = 0; i < 4; i++) {
            if (model.owner[i] == reader) {
                owned++;
                if (BOOK_BY_COPY[i] == target) {
                    return ALREADY_BORROWED_TITLE;
                }
            }
            if (model.owner[i] < 0 && BOOK_BY_COPY[i] == target) {
                free++;
            }
        }
        if (command == 1) {
            return queue.contains(reader) ? ALREADY_RESERVED : null;
        }
        if (owned >= LIMITS[reader]) {
            return LOAN_LIMIT_REACHED;
        }
        if (free == 0) {
            return NO_COPY_AVAILABLE;
        }
        // Budujemy zbiór uprawnionych, zamiast kopiować warunek pozycji z Library.
        var entitled = new HashSet<>(queue.subList(0, Math.min(free, queue.size())));
        if (!entitled.contains(reader) && free <= queue.size()) {
            return RESERVATION_PRIORITY;
        }
        return null;
    }

    private static void assertView(LibrarySnapshot view, int[] owner, LoanId[] ids, boolean[] renewed,
            List<List<Integer>> queues) {
        assertEquals(Arrays.stream(owner).filter(value -> value >= 0).count(), view.loans().size());
        for (int i = 0; i < 4; i++) {
            Loan loan = view.loans().get(copy(i));
            if (owner[i] < 0) {
                assertNull(loan);
            } else {
                assertEquals(member(owner[i]), loan.memberId());
                assertEquals(copy(i), loan.copyId());
                assertEquals(ids[i], loan.id());
                assertEquals(renewed[i], loan.renewed());
                int days = owner[i] == 2 ? 28 : 14;
                assertEquals(Fixtures.NOW, loan.borrowedAt());
                assertEquals(Fixtures.NOW.plus(java.time.Duration.ofDays(days * (renewed[i] ? 2 : 1))), loan.dueAt());
            }
        }
        for (int title = 0; title < 3; title++) {
            var expectedFree = new ArrayList<CopyId>();
            for (int i = 0; i < 4; i++) {
                if (BOOK_BY_COPY[i] == title && owner[i] < 0) {
                    expectedFree.add(copy(i));
                }
            }
            assertEquals(expectedFree, view.availableCopies(book(title)).stream().map(BookCopy::id).toList());
            assertEquals(queues.get(title).stream().map(GeneratedHistoryTest::member).toList(),
                    view.queueFor(book(title)));
            assertEquals(queues.get(title).size(), new HashSet<>(queues.get(title)).size());
        }
        for (int reader = 0; reader < 3; reader++) {
            var active = view.activeLoans(member(reader));
            assertTrue(active.size() <= LIMITS[reader]);
            assertEquals(active.size(), active.stream().map(loan -> view.copies().get(loan.copyId()).bookId())
                    .distinct().count());
        }
    }

    private static BookId book(int index) {
        return new BookId("B" + index);
    }

    private static CopyId copy(int index) {
        return new CopyId("C" + index);
    }

    private static MemberId member(int index) {
        return new MemberId("M" + index);
    }
}
