package pl.jakubtworek.lld.library;

import java.time.*;
import java.util.*;
import java.util.function.Function;
import static pl.jakubtworek.lld.library.LibraryException.Reason.*;

/**
 * Rdzeń techniczny: TreeSet wolnych kopii per tytuł, HashMap aktywnych wypożyczeń,
 * indeks czytelnik->tytuły i kolejka AVL z rangą. Nie skanujemy całego magazynu
 * ani wszystkich wypożyczeń przy borrow. Koszt pełnych kopii ponosi snapshot,
 * materializowany leniwie, nie każda operacja aktualizująca indeksy.
 * Wszelkie odmowy i obliczenia czasu poprzedzają zapis. Obsługa jednowątkowa.
 */
public final class Library {

    private final Clock clock;
    private final Map<BookId, Book> books;
    private final Map<CopyId, BookCopy> copies;
    private final Map<MemberId, Member> members;
    private final Map<CopyId, Loan> loans = new HashMap<>();
    private final Map<BookId, NavigableSet<BookCopy>> free = new HashMap<>();
    private final Map<MemberId, Set<BookId>> borrowed = new HashMap<>();
    private final Map<BookId, ReservationQueue> queues = new HashMap<>();
    private LibrarySnapshot cached;

    public Library(List<Book> books, List<BookCopy> copies, List<Member> members, Clock clock) {
        this.clock = Objects.requireNonNull(clock);
        this.books = unique(books, Book::id);
        this.copies = unique(copies, BookCopy::id);
        this.members = unique(members, Member::id);
        for (var book : this.books.keySet()) free.put(book, new TreeSet<>(Comparator.comparing(c -> c.id().value())));
        for (var copy : this.copies.values()) {
            if (!this.books.containsKey(copy.bookId()))
                throw new IllegalArgumentException("Egzemplarz wskazuje nieznany tytuł");
            free.get(copy.bookId()).add(copy);
        }
    }

    public LibrarySnapshot snapshot() {
        if (cached == null) {
            var reservations = new HashMap<BookId, List<MemberId>>();
            queues.forEach((book, queue) -> {
                if (!queue.isEmpty())
                    reservations.put(book, queue.snapshot());
            });
            cached = new LibrarySnapshot(books, copies, members, loans, reservations);
        }
        return cached;
    }

    public Loan borrow(MemberId memberId, BookId bookId) {
        Objects.requireNonNull(memberId);
        Objects.requireNonNull(bookId);
        var member = member(memberId);
        requireBook(bookId);
        requireNotBorrowed(memberId, bookId);
        if (borrowed.getOrDefault(memberId, Set.of()).size() >= member.membership().loanLimit())
            throw new LibraryException(LOAN_LIMIT_REACHED, "Osiągnięty limit wypożyczeń");
        var available = free.get(bookId);
        if (available.isEmpty())
            throw new LibraryException(NO_COPY_AVAILABLE, "Brak wolnego egzemplarza");
        var queue = queues.get(bookId);
        int rank = queue == null ? -1 : queue.rank(memberId), size = queue == null ? 0 : queue.size();
        if (rank >= available.size() || rank < 0 && available.size() <= size)
            throw new LibraryException(RESERVATION_PRIORITY, "Wolne egzemplarze mają pierwszeństwo rezerwacji");
        var copy = available.first();
        Instant now = Objects.requireNonNull(clock.instant());
        var loan = new Loan(new LoanId(UUID.randomUUID()), copy.id(), memberId, now, now.plus(member.membership()
                .loanPeriod()), false);
        loans.put(copy.id(), loan);
        available.remove(copy);
        borrowed.computeIfAbsent(memberId, ignored -> new HashSet<>()).add(bookId);
        if (queue != null)
            queue.remove(memberId);
        cached = null;
        return loan;
    }

    public Loan returnCopy(MemberId memberId, CopyId copyId, LoanId loanId) {
        var loan = ownedLoan(memberId, copyId, loanId);
        var copy = copies.get(copyId);
        loans.remove(copyId);
        free.get(copy.bookId()).add(copy);
        borrowed.get(memberId).remove(copy.bookId());
        cached = null;
        return loan;
    }

    public Loan renew(MemberId memberId, CopyId copyId, LoanId loanId) {
        var current = ownedLoan(memberId, copyId, loanId);
        var queue = queues.get(copies.get(copyId).bookId());
        if (queue != null && !queue.isEmpty())
            throw new LibraryException(WAITING_RESERVATIONS, "Ktoś czeka na ten tytuł");
        Instant now = Objects.requireNonNull(clock.instant());
        var replacement = current.renew(now, members.get(memberId).membership().loanPeriod());
        loans.put(copyId, replacement);
        cached = null;
        return replacement;
    }

    public LibrarySnapshot reserve(MemberId memberId, BookId bookId) {
        Objects.requireNonNull(memberId);
        Objects.requireNonNull(bookId);
        member(memberId);
        requireBook(bookId);
        requireNotBorrowed(memberId, bookId);
        var queue = queues.computeIfAbsent(bookId, ignored -> new ReservationQueue());
        if (queue.contains(memberId))
            throw new LibraryException(ALREADY_RESERVED, "Czytelnik już czeka na ten tytuł");
        queue.add(memberId);
        cached = null;
        return snapshot();
    }

    public LibrarySnapshot cancelReservation(MemberId memberId, BookId bookId) {
        Objects.requireNonNull(memberId);
        Objects.requireNonNull(bookId);
        member(memberId);
        requireBook(bookId);
        var queue = queues.get(bookId);
        if (queue == null || !queue.contains(memberId))
            throw new LibraryException(NO_RESERVATION, "Brak rezerwacji");
        queue.remove(memberId);
        cached = null;
        return snapshot();
    }

    private Loan ownedLoan(MemberId memberId, CopyId copyId, LoanId loanId) {
        Objects.requireNonNull(memberId);
        Objects.requireNonNull(copyId);
        Objects.requireNonNull(loanId);
        member(memberId);
        if (!copies.containsKey(copyId))
            throw new LibraryException(UNKNOWN_COPY, "Nieznany egzemplarz");
        var loan = loans.get(copyId);
        if (loan == null)
            throw new LibraryException(COPY_NOT_ON_LOAN, "Egzemplarz nie jest wypożyczony");
        if (!loan.memberId().equals(memberId))
            throw new LibraryException(NOT_BORROWER, "Inny czytelnik wypożyczył egzemplarz");
        if (!loan.id().equals(loanId))
            throw new LibraryException(STALE_LOAN, "Identyfikator nie pasuje do aktywnego wypożyczenia");
        return loan;
    }

    private Member member(MemberId id) {
        var member = members.get(id);
        if (member == null)
            throw new LibraryException(UNKNOWN_MEMBER, "Nieznany czytelnik");
        return member;
    }

    private void requireBook(BookId id) {
        if (!books.containsKey(id))
            throw new LibraryException(UNKNOWN_BOOK, "Nieznany tytuł");
    }

    private void requireNotBorrowed(MemberId memberId, BookId bookId) {
        if (borrowed.getOrDefault(memberId, Set.of()).contains(bookId))
            throw new LibraryException(ALREADY_BORROWED_TITLE, "Czytelnik już wypożyczył ten tytuł");
    }

    private static <K, V> Map<K, V> unique(List<V> values, Function<V, K> id) {
        var result = new HashMap<K, V>();
        for (var value : List.copyOf(values)) if (result.putIfAbsent(id.apply(value), value) != null)
            throw new IllegalArgumentException("Powtórzony identyfikator w konfiguracji");
        return Map.copyOf(result);
    }
}
