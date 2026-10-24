package pl.jakubtworek.lld.parkinglot;

import java.util.Objects;

/**
 * Stały opis miejsca: identyfikator, piętro i typ. Nie przechowuje flagi occupied.
 * Zajętość wynika z aktywnych biletów. Dwie mutowalne reprezentacje tego samego
 * faktu wymagałyby synchronizacji przy każdym wjeździe i wyjeździe.
 * ID musi być unikalne w parkingu; ten warunek sprawdza właściciel kolekcji.
 */
public record ParkingSpot(String id, int floor, SpotType type) {

    public ParkingSpot {
        id = Objects.requireNonNull(id, "id").strip();
        Objects.requireNonNull(type, "type");
        if (id.isEmpty() || floor < 0) {
            throw new IllegalArgumentException("Miejsce wymaga niepustego ID i nieujemnego piętra");
        }
    }
}
