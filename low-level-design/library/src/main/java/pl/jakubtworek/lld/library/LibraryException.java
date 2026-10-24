package pl.jakubtworek.lld.library;

/**
 * Stabilny kod odmowy oddzielamy od tekstu dla człowieka. Np. brak wolnej kopii
 * i pierwszeństwo rezerwacji to różne reguły i wymagają innych działań klienta.
 */
public final class LibraryException extends RuntimeException {

    public enum Reason {

        UNKNOWN_BOOK,
        UNKNOWN_COPY,
        UNKNOWN_MEMBER,
        ALREADY_BORROWED_TITLE,
        LOAN_LIMIT_REACHED,
        NO_COPY_AVAILABLE,
        RESERVATION_PRIORITY,
        ALREADY_RESERVED,
        NO_RESERVATION,
        COPY_NOT_ON_LOAN,
        NOT_BORROWER,
        STALE_LOAN,
        WAITING_RESERVATIONS,
        ALREADY_RENEWED,
        LOAN_OVERDUE
    }

    private final Reason reason;

    public LibraryException(Reason reason, String message) {
        super(message);
        this.reason = java.util.Objects.requireNonNull(reason, "reason");
    }

    public Reason reason() {
        return reason;
    }
}
