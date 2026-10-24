package pl.jakubtworek.lld.carrental;

import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import static pl.jakubtworek.lld.carrental.RentalException.Reason.*;

/**
 * Jedna publikacja kalendarza. Wszystkie wartości wewnątrz map są niemutowalne,
 * więc Map.copyOf wystarcza bez kopiowania mutowalnych wnętrz. Widok dostępności
 * jest PLANEM: aktywny spóźniony klient może fizycznie zablokować przyszły odbiór.
 * Odczyt nie przesuwa czasu i nie zmienia automatycznie statusów rezerwacji.
 */
public final class RentalSnapshot {

    private final CalendarIndex calendar = new CalendarIndex();
    private final Map<VehicleId, Vehicle> vehicles;
    private final Map<CustomerId, Customer> customers;
    private final Map<ReservationId, Reservation> reservations;
    private final Map<MaintenanceId, Maintenance> maintenance;

    RentalSnapshot(Map<VehicleId, Vehicle> vehicles, Map<CustomerId, Customer> customers, Map<ReservationId,
            Reservation> reservations, Map<MaintenanceId, Maintenance> maintenance) {
        this.vehicles = Map.copyOf(vehicles);
        this.customers = Map.copyOf(customers);
        this.reservations = Map.copyOf(reservations);
        this.maintenance = Map.copyOf(maintenance);
        this.reservations.values().forEach(calendar::add);
        this.maintenance.values().forEach(calendar::add);
    }

    public Map<VehicleId, Vehicle> vehicles() {
        return vehicles;
    }

    public Map<CustomerId, Customer> customers() {
        return customers;
    }

    public Map<ReservationId, Reservation> reservations() {
        return reservations;
    }

    public Map<MaintenanceId, Maintenance> maintenance() {
        return maintenance;
    }

    /**
     * Historyczne daty są dozwolone w kwerendzie, nie oznaczają możliwości nowej umowy.
     */
    public java.util.List<Vehicle> availableVehicles(RentalPeriod period) {
        Objects.requireNonNull(period, "period");
        return vehicles.values().stream().filter(vehicle -> conflict(vehicle.id(), period).isEmpty())
                .sorted(Comparator.comparing(vehicle -> vehicle.id().value())).toList();
    }

    public boolean isAvailable(VehicleId vehicleId, RentalPeriod period) {
        Objects.requireNonNull(vehicleId, "vehicleId");
        Objects.requireNonNull(period, "period");
        if (!vehicles.containsKey(vehicleId)) {
            throw new RentalException(UNKNOWN_VEHICLE, "Nieznany pojazd");
        }
        return conflict(vehicleId, period).isEmpty();
    }

    /**
     * Jedno deterministyczne źródło odmowy: umowa przed serwisem, następnie
     * najwcześniejszy początek i UUID przy remisie. Nie zależymy od iteracji map.
     * To nie lista wszystkich konfliktów i nie obietnica gotowości fizycznej auta.
     */
    public Optional<CalendarConflict> conflict(VehicleId vehicleId, RentalPeriod period) {
        return conflictExcluding(vehicleId, period, null);
    }

    // Pominięcie ID jest dostępne tylko wewnątrz modelu przy zmianie terminu.
    // Publiczna kwerenda nie pozwala klientowi przemilczeć wybranej umowy.
    Optional<CalendarConflict> conflictExcluding(VehicleId vehicleId, RentalPeriod period, ReservationId excluded) {
        Objects.requireNonNull(vehicleId, "vehicleId");
        Objects.requireNonNull(period, "period");
        if (!vehicles.containsKey(vehicleId)) {
            throw new RentalException(UNKNOWN_VEHICLE, "Nieznany pojazd");
        }
        return calendar.conflict(vehicleId, period, excluded);
    }
}
