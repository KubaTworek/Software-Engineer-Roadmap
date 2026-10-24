package pl.jakubtworek.lld.meetingrooms;

/**
 * CONFIRMED pozostaje w historii także po końcu spotkania; nie dodajemy stanu zależnego od samoczynnego zadania.
 */
public enum BookingStatus {

    CONFIRMED, CANCELLED
}
