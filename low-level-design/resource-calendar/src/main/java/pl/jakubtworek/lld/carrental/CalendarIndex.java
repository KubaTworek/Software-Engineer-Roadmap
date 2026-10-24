package pl.jakubtworek.lld.carrental;

import pl.jakubtworek.lld.calendar.IntervalCalendar;
import java.time.LocalDate;
import java.util.*;

/**
 * Adapter dat i typowanej odmowy do wspólnego indeksu przedziałów.
 * Dwa kalendarze na auto zachowują pierwszeństwo umowy przed serwisem.
 * IntervalCalendar odpowiada za porządek i kolizje; adapter zachowuje typy
 * identyfikatorów i tłumaczy wynik na właściwą odmowę bez kopiowania algorytmu.
 */
final class CalendarIndex {

    private final Map<VehicleId, IntervalCalendar<LocalDate, ReservationId, Reservation>> rentals = new HashMap<>();
    private final Map<VehicleId, IntervalCalendar<LocalDate, MaintenanceId, Maintenance>> repairs = new HashMap<>();

    // Generyki rozdzielają ID rezerwacji i serwisu w czasie kompilacji.
    // Mapy nadrzędne tworzą indeks zasobu, bez skanowania historii innych aut.
    void add(Reservation value) {
        if (blocking(value)) {
            rentals.computeIfAbsent(value.vehicleId(), ignored -> new IntervalCalendar<>()).put(value.id(),
                    value.period().start(), value.period().end(), value);
        }
    }

    void remove(Reservation value) {
        var calendar = rentals.get(value.vehicleId());
        if (calendar != null && blocking(value)) {
            calendar.remove(value.id());
        }
    }

    void add(Maintenance value) {
        repairs.computeIfAbsent(value.vehicleId(), ignored -> new IntervalCalendar<>()).put(value.id(), value.period()
                .start(), value.period().end(), value);
    }

    void remove(Maintenance value) {
        repairs.get(value.vehicleId()).remove(value.id());
    }

    Optional<CalendarConflict> conflict(VehicleId vehicle, RentalPeriod period, ReservationId excluded) {
        var calendar = rentals.get(vehicle);
        if (calendar != null) {
            var found = calendar.firstConflict(period.start(), period.end(), excluded);
            if (found.isPresent()) {
                var value = found.orElseThrow();
                return Optional.of(new CalendarConflict.ReservationConflict(value.id(), vehicle, value.period()));
            }
        }
        var service = repairs.get(vehicle);
        if (service == null) {
            return Optional.empty();
        }
        return service.firstConflict(period.start(), period.end(), null)
                .map(value -> new CalendarConflict.MaintenanceConflict(value.id(), vehicle, value.period()));
    }

    private static boolean blocking(Reservation value) {
        return value.status() == ReservationStatus.RESERVED || value.status() == ReservationStatus.ACTIVE;
    }
}
