package pl.jakubtworek.lld.library;

import java.time.Instant;
import java.time.Duration;
import java.util.Objects;

/**
 * Niemutowalne warunki konkretnego wypożyczenia. Aktywność wynika z obecności
 * w LibrarySnapshot.loans(), nie z mutowanej flagi tutaj. Po zwrocie klient
 * może zachować ten obiekt, ale nie jest on dowodem nadal aktywnego wypożyczenia.
 * Nie przyjmujemy obiektów Loan jako poleceń zmiany stanu biblioteki.
 */
public record Loan(LoanId id, CopyId copyId, MemberId memberId, Instant borrowedAt, Instant dueAt, boolean renewed) {

    public Loan {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(copyId, "copyId");
        Objects.requireNonNull(memberId, "memberId");
        Objects.requireNonNull(borrowedAt, "borrowedAt");
        Objects.requireNonNull(dueAt, "dueAt");
        if (!dueAt.isAfter(borrowedAt)) {
            throw new IllegalArgumentException("Termin musi być po wypożyczeniu");
        }
    }

    /**
     * Nowa wersja tej samej relacji: ID i chwila odbioru się nie zmieniają.
     * Okres dodajemy do terminu, nie do now: wczesne przedłużenie nie odbiera
     * pozostałych dni. Kolejkę sprawdza Library, bo Loan nie zna innych osób.
     * Przy równości now==dueAt termin nie jest jeszcze przekroczony.
     */
    Loan renew(Instant now, Duration period) {
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(period, "period");
        if (renewed) {
            throw new LibraryException(LibraryException.Reason.ALREADY_RENEWED, "Wypożyczenie było już przedłużane");
        }
        if (now.isBefore(borrowedAt)) {
            throw new IllegalStateException("Zegar wskazuje czas przed wypożyczeniem");
        }
        if (isPastDueAt(now)) {
            throw new LibraryException(LibraryException.Reason.LOAN_OVERDUE, "Termin zwrotu został przekroczony");
        }
        if (period.isNegative() || period.isZero()) {
            throw new IllegalArgumentException("Okres musi być dodatni");
        }
        return new Loan(id, copyId, memberId, borrowedAt, dueAt.plus(period), true);
    }

    /**
     * Równość z terminem nie jest spóźnieniem. To porównanie czasu, nie aktywności.
     */
    public boolean isPastDueAt(Instant now) {
        return Objects.requireNonNull(now, "now").isAfter(dueAt);
    }
}
