package pl.jakubtworek.lld.atm;

/**
 * Błędny PIN jest wynikiem ze zmianą licznika, a nie odmową pozostawiającą cały stan bez zmian.
 */
public enum AuthenticationResult {

    AUTHENTICATED, REJECTED, CARD_EJECTED
}
