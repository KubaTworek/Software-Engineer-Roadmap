package com.ridesharing.mvp.matching;

import java.util.UUID;

/**
 * Polecenie uruchomienia matchingu publikowane wewnątrz transakcji przejazdu.
 * Listener obsługuje je dopiero po commicie, dlatego worker nigdy nie próbuje
 * przypisać kierowcy do przejazdu, którego zapis nie jest jeszcze widoczny.
 */
public record MatchingRequestedEvent(UUID rideId, String cityId, double pickupLat, double pickupLng) {
}
