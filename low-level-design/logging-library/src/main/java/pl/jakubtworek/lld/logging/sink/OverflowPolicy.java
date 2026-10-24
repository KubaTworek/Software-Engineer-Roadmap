package pl.jakubtworek.lld.logging.sink;

/**
 * Pełny bufor: REJECT_NEW zachowuje historię i zgłasza błąd, DROP_OLDEST
 * usuwa najstarszy rekord i przyjmuje nowy. To limit rekordów, nie bajtów.
 */
public enum OverflowPolicy {

    REJECT_NEW, DROP_OLDEST
}
