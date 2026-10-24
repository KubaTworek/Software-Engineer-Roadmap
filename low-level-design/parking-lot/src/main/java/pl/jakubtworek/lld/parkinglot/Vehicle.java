package pl.jakubtworek.lld.parkinglot;

import java.util.Objects;

/**
 * Niemutowalny opis pojazdu. Record porównuje wszystkie składowe, lecz reguła
 * „pojazd już parkuje” używa samej rejestracji. Inaczej klient mógłby ponownie
 * wjechać tą samą rejestracją, deklarując inny typ. To rozróżnienie równości
 * wartości opisu i tożsamości biznesowej sprawdza ParkingLotTest.
 */
public record Vehicle(LicensePlate plate, VehicleType type) {

    public Vehicle {
        Objects.requireNonNull(plate, "plate");
        Objects.requireNonNull(type, "type");
    }
}
