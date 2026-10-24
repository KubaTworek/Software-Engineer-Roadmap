package pl.jakubtworek.lld.parkinglot;

/**
 * Typ pojazdu potrzebny do dopasowania miejsca. Cel edukacyjny: nie budujemy
 * hierarchii Motorcycle extends Vehicle, gdy jedyną różnicą jest klasyfikacja.
 * Zachowanie przydziału zależy od zgodności z miejscem, a nie metod podklasy.
 */
public enum VehicleType {

    MOTORCYCLE, CAR, VAN
}
