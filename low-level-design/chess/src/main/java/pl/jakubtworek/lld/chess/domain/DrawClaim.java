package pl.jakubtworek.lld.chess.domain;

/**
 * Uprawnienia do zgłoszenia, które same nie kończą gry po trzecim powtórzeniu/100 półruchach.
 */
public enum DrawClaim {

    THREEFOLD_REPETITION, FIFTY_MOVE_RULE
}
