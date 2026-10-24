package pl.jakubtworek.lld.library;

import java.util.Objects;
import java.util.UUID;

/**
 * Tożsamość jednego wypożyczenia, niezależna od powtarzającej się pary
 * czytelnik–egzemplarz. Nowy odbiór dostaje nowy UUID, przedłużenie zachowuje ID.
 * Losowy identyfikator nie jest autoryzacją ani kluczem idempotencji: nie
 * przechowujemy wyników zamkniętych operacji. UUID pozwala też odróżniać
 * wypożyczenia utworzone w różnych instancjach biblioteki.
 */
public record LoanId(UUID value) {

    public LoanId {
        Objects.requireNonNull(value, "value");
    }

    public static LoanId parse(String value) {
        return new LoanId(UUID.fromString(Objects.requireNonNull(value, "value").strip()));
    }
}
