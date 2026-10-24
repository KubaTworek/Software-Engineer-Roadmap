package pl.jakubtworek.lld.carrental;

import java.util.Objects;

/**
 * Planowane wyłączenie zasobu. Nie jest klientem ani specjalnym statusem rezerwacji.
 */
public record Maintenance(MaintenanceId id, VehicleId vehicleId, RentalPeriod period, String reason) {

    public Maintenance {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(vehicleId, "vehicleId");
        Objects.requireNonNull(period, "period");
        reason = Text.nonBlank(reason);
    }
}
