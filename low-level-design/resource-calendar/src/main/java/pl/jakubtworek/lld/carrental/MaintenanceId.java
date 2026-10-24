package pl.jakubtworek.lld.carrental;

import java.util.Objects;
import java.util.UUID;

/**
 * Osobna tożsamość wyłączenia zapobiega usunięciu rezerwacji komendą serwisową.
 */
public record MaintenanceId(UUID value) {

    public MaintenanceId {
        Objects.requireNonNull(value, "value");
    }

    public static MaintenanceId parse(String value) {
        return new MaintenanceId(UUID.fromString(Text.nonBlank(value)));
    }
}
