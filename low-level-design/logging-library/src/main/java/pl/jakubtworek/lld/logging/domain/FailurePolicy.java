package pl.jakubtworek.lld.logging.domain;

/**
 * Polityka dotyczy awarii zapisu. Nie przechwytuje błędów Supplier/masker/Clock/formatter
 * ani Error. STOP nie wycofuje wcześniejszych zapisów; CONTINUE nie maskuje odmów.
 */
public enum FailurePolicy {

    CONTINUE, STOP_ON_FAILURE
}
