package pl.jakubtworek.lld.carrental;

import java.util.Objects;

/**
 * Konkretny zasób floty. Nie ma flagi available: to samo auto może być wolne
 * w jednym przedziale i zajęte w innym. Dostępność wynika z kalendarza agencji.
 */
public record Vehicle(VehicleId id, String model, VehicleCategory category) {

    public Vehicle {
        Objects.requireNonNull(id, "id");
        model = Text.nonBlank(model);
        Objects.requireNonNull(category, "category");
    }
}
