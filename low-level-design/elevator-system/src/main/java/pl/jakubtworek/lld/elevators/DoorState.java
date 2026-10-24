package pl.jakubtworek.lld.elevators;

/**
 * Otwarcie następuje przy obsłużeniu celu. Zamknięcie wymaga osobnego tick, więc nie jedziemy w kroku zamykania.
 */
public enum DoorState {

    OPEN, CLOSED
}
