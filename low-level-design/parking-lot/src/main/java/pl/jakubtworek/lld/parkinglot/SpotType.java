package pl.jakubtworek.lld.parkinglot;

import java.util.Objects;

/**
 * Reguła zgodności należy do rodzaju miejsca. Jawna macierz jest czytelniejsza
 * niż porównywanie ordinal(): przestawienie enumów nie może zmienić fizycznej
 * zgodności. capacityRank to osobna reguła preferencji strategii best-fit.
 */
public enum SpotType {

    MOTORCYCLE, COMPACT, LARGE;

    public boolean accommodates(VehicleType vehicleType) {
        Objects.requireNonNull(vehicleType, "vehicleType");
        return switch(this) {
            case MOTORCYCLE ->
                vehicleType == VehicleType.MOTORCYCLE;
            case COMPACT ->
                vehicleType != VehicleType.VAN;
            case LARGE ->
                true;
        };
    }

    int capacityRank() {
        return switch(this) {
            case MOTORCYCLE ->
                0;
            case COMPACT ->
                1;
            case LARGE ->
                2;
        };
    }
}
