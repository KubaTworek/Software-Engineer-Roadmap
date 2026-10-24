package pl.jakubtworek.lld.promotion.domain;

/**
 * EXCLUSIVE wyklucza wszystkie inne zastosowane rabaty, niezależnie od ich indywidualnych ID.
 */
public enum StackingPolicy {

    STACKABLE, EXCLUSIVE
}
