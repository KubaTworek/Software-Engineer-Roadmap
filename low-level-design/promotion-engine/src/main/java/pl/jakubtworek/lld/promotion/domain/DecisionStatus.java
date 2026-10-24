package pl.jakubtworek.lld.promotion.domain;

/**
 * Wynik oceny pojedynczej promocji. Zwykła niekwalifikacja nie jest wyjątkiem.
 * Kolejność sprawdzeń określa, który z kilku możliwych powodów trafi do raportu.
 */
public enum DecisionStatus {

    APPLIED,
    CONDITION_NOT_MET,
    BLOCKED_EXCLUSIVE,
    EXCLUDED,
    NO_REMAINING_BALANCE,
    LIMIT_REACHED,
    NO_REDUCTION,
    NO_ELIGIBLE_BALANCE,
    NOT_ACTIVE,
    NOT_SELECTED
}
