package pl.jakubtworek.lld.library;

import java.util.Objects;

/**
 * Czytelnik z parametrami uprawnień. Liczbę wypożyczeń wyliczamy z relacji Loan,
 * nie utrzymujemy drugiego licznika wymagającego aktualizacji przy zwrocie.
 */
public record Member(MemberId id, String name, Membership membership) {

    public Member {
        Objects.requireNonNull(id, "id");
        name = Text.nonBlank(name);
        Objects.requireNonNull(membership, "membership");
    }
}
