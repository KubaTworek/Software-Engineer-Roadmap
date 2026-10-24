package pl.jakubtworek.lld.library;

import java.time.Duration;

/**
 * Dwa rzeczywiste zestawy parametrów, nie dwa różne algorytmy. Enum wystarcza;
 * dziedziczenie StandardMember/ExtendedMember powieliłoby ten sam cykl życia.
 * Doba oznacza dokładnie 24 godziny, nie koniec dnia w lokalnym kalendarzu.
 */
public enum Membership {

    STANDARD(2, Duration.ofDays(14)), EXTENDED(5, Duration.ofDays(28));

    private final int loanLimit;
    private final Duration loanPeriod;

    Membership(int loanLimit, Duration loanPeriod) {
        this.loanLimit = loanLimit;
        this.loanPeriod = loanPeriod;
    }

    public int loanLimit() {
        return loanLimit;
    }

    public Duration loanPeriod() {
        return loanPeriod;
    }
}
