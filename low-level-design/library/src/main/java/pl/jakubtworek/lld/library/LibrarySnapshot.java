package pl.jakubtworek.lld.library;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Widok jednej publikacji, niezależny od następnych poleceń. Map.copyOf nie
 * kopiuje wnętrza list: kolejki zabezpieczamy osobno List.copyOf. Ich kolejność
 * jest regułą domenową, podczas gdy porządek iteracji map nie ma znaczenia.
 * Konstruktor pakietowy ogranicza składanie stanu do kodu modelu.
 */
public final class LibrarySnapshot {

    private final Map<BookId, Book> books;
    private final Map<CopyId, BookCopy> copies;
    private final Map<MemberId, Member> members;
    private final Map<CopyId, Loan> loans;
    private final Map<BookId, List<MemberId>> reservations;

    LibrarySnapshot(Map<BookId, Book> books, Map<CopyId, BookCopy> copies, Map<MemberId, Member> members, Map<CopyId,
            Loan> loans, Map<BookId, List<MemberId>> reservations) {
        this.books = Map.copyOf(books);
        this.copies = Map.copyOf(copies);
        this.members = Map.copyOf(members);
        this.loans = Map.copyOf(loans);
        var queues = new HashMap<BookId, List<MemberId>>();
        reservations.forEach((book, queue) -> queues.put(book, List.copyOf(queue)));
        this.reservations = Map.copyOf(queues);
    }

    public Map<BookId, Book> books() {
        return books;
    }

    public Map<CopyId, BookCopy> copies() {
        return copies;
    }

    public Map<MemberId, Member> members() {
        return members;
    }

    public Map<CopyId, Loan> loans() {
        return loans;
    }

    public Map<BookId, List<MemberId>> reservations() {
        return reservations;
    }

    /**
     * Wolny egzemplarz może mieć pierwszeństwo odbioru dla osoby z kolejki.
     */
    public List<BookCopy> availableCopies(BookId bookId) {
        requireBook(bookId);
        return copies.values().stream().filter(copy -> copy.bookId().equals(bookId))
                .filter(copy -> !loans.containsKey(copy.id())).sorted(Comparator.comparing(copy -> copy.id().value()))
                .toList();
    }

    public List<MemberId> queueFor(BookId bookId) {
        requireBook(bookId);
        return reservations.getOrDefault(bookId, List.of());
    }

    public List<Loan> activeLoans(MemberId memberId) {
        Objects.requireNonNull(memberId, "memberId");
        if (!members.containsKey(memberId)) {
            throw new LibraryException(LibraryException.Reason.UNKNOWN_MEMBER, "Nieznany czytelnik");
        }
        return loans.values().stream().filter(loan -> loan.memberId().equals(memberId))
                .sorted(Comparator.comparing(loan -> loan.copyId().value())).toList();
    }

    private void requireBook(BookId bookId) {
        Objects.requireNonNull(bookId, "bookId");
        if (!books.containsKey(bookId)) {
            throw new LibraryException(LibraryException.Reason.UNKNOWN_BOOK, "Nieznany tytuł");
        }
    }
}
