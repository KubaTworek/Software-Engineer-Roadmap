package pl.jakubtworek.lld.carrental;

import java.util.Objects;
import java.util.UUID;

/**
 * Dwa rodzaje źródła konfliktu mają różne typy ID. Sealed interface pozwala
 * wyrazić tę alternatywę bez dwóch pól Optional i niejasnego przypadku obu naraz.
 * Wynik opisuje blokujący przedział, nie wyłącznie część wspólną z zapytaniem.
 * Jest niemutowalnym opisem danego widoku, a nie blokadą zasobu na przyszłość.
 */
public sealed interface CalendarConflict permits CalendarConflict.ReservationConflict,
        CalendarConflict.MaintenanceConflict {

    RentalException.Reason reason();

    VehicleId vehicleId();

    RentalPeriod period();

    UUID sourceId();

    record ReservationConflict(ReservationId id, VehicleId vehicleId, RentalPeriod period) implements CalendarConflict {

        public ReservationConflict {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(vehicleId, "vehicleId");
            Objects.requireNonNull(period, "period");
        }

        @Override
        public RentalException.Reason reason() {
            return RentalException.Reason.RESERVATION_CONFLICT;
        }

        @Override
        public UUID sourceId() {
            return id.value();
        }
    }

    record MaintenanceConflict(MaintenanceId id, VehicleId vehicleId, RentalPeriod period) implements CalendarConflict {

        public MaintenanceConflict {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(vehicleId, "vehicleId");
            Objects.requireNonNull(period, "period");
        }

        @Override
        public RentalException.Reason reason() {
            return RentalException.Reason.MAINTENANCE_CONFLICT;
        }

        @Override
        public UUID sourceId() {
            return id.value();
        }
    }
}
