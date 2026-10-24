package pl.jakubtworek.lld.carrental;

/**
 * Stan umowy, nie globalny stan pojazdu. Jedno auto ma wiele umów w różnych terminach.
 */
public enum ReservationStatus {

    RESERVED, ACTIVE, COMPLETED, CANCELLED
}
