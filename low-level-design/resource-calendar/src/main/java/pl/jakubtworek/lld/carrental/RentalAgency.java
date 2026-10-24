package pl.jakubtworek.lld.carrental;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import static pl.jakubtworek.lld.carrental.RentalException.Reason.*;

/**
 * Granica spójności floty, rezerwacji i serwisu. Reservation chroni swój cykl,
 * ale nie wie o innych umowach. Tutaj odrzucamy nakładanie się planów i drugie
 * fizyczne wydanie auta. Nie ma globalnej flagi dostępności pojazdu.
 *
 * Indeksy kalendarza zmieniamy dopiero po walidacji i obliczeniach mogących
 * odmówić operacji. Snapshot kopiujemy leniwie przy odczycie, a nie przy zapisie.
 * Model jest jednowątkowy; indeksy nie stanowią synchronizacji.
 */
public final class RentalAgency {

    private final Clock clock;
    private final Map<VehicleId, Vehicle> vehicles;
    private final Map<CustomerId, Customer> customers;
    private final Map<ReservationId, Reservation> reservations = new HashMap<>();
    private final Map<MaintenanceId, Maintenance> maintenance = new HashMap<>();
    private final Map<VehicleId, ReservationId> active = new HashMap<>();
    private final CalendarIndex calendar = new CalendarIndex();
    private RentalSnapshot cached;

    public RentalAgency(List<Vehicle> vehicles, List<Customer> customers, Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.vehicles = Map.copyOf(unique(vehicles, Vehicle::id));
        this.customers = Map.copyOf(unique(customers, Customer::id));
    }

    public RentalSnapshot snapshot() {
        if (cached == null) {
            cached = new RentalSnapshot(vehicles, customers, reservations, maintenance);
        }
        return cached;
    }

    public Reservation reserve(CustomerId customerId, VehicleId vehicleId, RentalPeriod period) {
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(vehicleId, "vehicleId");
        Objects.requireNonNull(period, "period");
        customer(customerId);
        Vehicle vehicle = vehicle(vehicleId);
        requireNotPast(period);
        requireAvailable(vehicleId, period);
        var reservation = Reservation.reserved(new ReservationId(UUID.randomUUID()), vehicle, customerId, period);
        publish(reservation);
        return reservation;
    }

    public Reservation cancel(CustomerId customerId, ReservationId id) {
        Reservation current = owned(customerId, id);
        // Bez Clock: można anulować także nieodebraną umowę po terminie.
        Reservation next = current.cancel();
        publish(next);
        return next;
    }

    /**
     * Nie wykonujemy cancel+reserve: odmowa nowego terminu nie może odebrać
     * starego. Pomijamy dokładnie własne ID, a nie wszystkie umowy klienta
     * lub auta. Dzięki temu zmiana na częściowo pokrywający się okres jest legalna.
     */
    public Reservation reschedule(CustomerId customerId, ReservationId id, RentalPeriod replacement) {
        Objects.requireNonNull(replacement, "replacement");
        Reservation current = owned(customerId, id);
        current.requireStatus(ReservationStatus.RESERVED);
        requireNotPast(replacement);
        calendar.conflict(current.vehicleId(), replacement, id).ifPresent(conflict -> {
            throw new RentalException(conflict);
        });
        Reservation next = current.rescheduled(replacement);
        publish(next);
        return next;
    }

    public Reservation pickup(CustomerId customerId, ReservationId id) {
        Reservation current = owned(customerId, id);
        current.requireStatus(ReservationStatus.RESERVED);
        boolean stillOut = active.containsKey(current.vehicleId());
        if (stillOut) {
            throw new RentalException(VEHICLE_NOT_RETURNED, "Poprzedni klient nie zwrócił pojazdu");
        }
        Reservation next = current.pickup(LocalDate.now(clock));
        publish(next);
        return next;
    }

    public RentalReceipt returnVehicle(CustomerId customerId, ReservationId id) {
        Reservation current = owned(customerId, id);
        current.requireStatus(ReservationStatus.ACTIVE);
        Reservation next = current.complete(LocalDate.now(clock));
        publish(next);
        return next.receipt().orElseThrow();
    }

    public Maintenance scheduleMaintenance(VehicleId vehicleId, RentalPeriod period, String reason) {
        Objects.requireNonNull(vehicleId, "vehicleId");
        Objects.requireNonNull(period, "period");
        String description = Text.nonBlank(reason);
        vehicle(vehicleId);
        requireNotPast(period);
        requireAvailable(vehicleId, period);
        var block = new Maintenance(new MaintenanceId(UUID.randomUUID()), vehicleId, period, description);
        maintenance.put(block.id(), block);
        calendar.add(block);
        cached = null;
        return block;
    }

    /**
     * Usunięcie planu serwisu nie oznacza fizycznego potwierdzenia naprawy.
     */
    public Maintenance removeMaintenance(MaintenanceId id) {
        Objects.requireNonNull(id, "id");
        Maintenance block = maintenance.get(id);
        if (block == null) {
            throw new RentalException(UNKNOWN_MAINTENANCE, "Nieznane wyłączenie serwisowe");
        }
        maintenance.remove(id);
        calendar.remove(block);
        cached = null;
        return block;
    }

    private void publish(Reservation reservation) {
        Reservation previous = reservations.get(reservation.id());
        if (previous != null) {
            calendar.remove(previous);
            // Zmiana przyszłej rezerwacji nie może usunąć aktywnego wydania
            // tego samego auta z INNEJ umowy. Usuwamy parę klucz-wartość.
            active.remove(previous.vehicleId(), previous.id());
        }
        reservations.put(reservation.id(), reservation);
        calendar.add(reservation);
        if (reservation.status() == ReservationStatus.ACTIVE) {
            active.put(reservation.vehicleId(), reservation.id());
        }
        cached = null;
    }

    private void requireNotPast(RentalPeriod period) {
        if (period.start().isBefore(LocalDate.now(clock))) {
            throw new RentalException(PAST_PERIOD, "Początek nie może być w przeszłości");
        }
    }

    private void requireAvailable(VehicleId id, RentalPeriod period) {
        calendar.conflict(id, period, null).ifPresent(conflict -> {
            throw new RentalException(conflict);
        });
    }

    private Reservation owned(CustomerId customerId, ReservationId id) {
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(id, "id");
        customer(customerId);
        Reservation reservation = reservations.get(id);
        if (reservation == null) {
            throw new RentalException(UNKNOWN_RESERVATION, "Nieznana rezerwacja");
        }
        if (!reservation.customerId().equals(customerId)) {
            throw new RentalException(NOT_CUSTOMER, "Inny klient jest właścicielem umowy");
        }
        return reservation;
    }

    private Vehicle vehicle(VehicleId id) {
        Vehicle vehicle = vehicles.get(id);
        if (vehicle == null) {
            throw new RentalException(UNKNOWN_VEHICLE, "Nieznany pojazd");
        }
        return vehicle;
    }

    private void customer(CustomerId id) {
        if (!customers.containsKey(id)) {
            throw new RentalException(UNKNOWN_CUSTOMER, "Nieznany klient");
        }
    }

    private static <K, V> Map<K, V> unique(List<V> values, Function<V, K> id) {
        var result = new HashMap<K, V>();
        for (V value : List.copyOf(values)) {
            if (result.putIfAbsent(id.apply(value), value) != null) {
                throw new IllegalArgumentException("Powtórzony kod konfiguracji");
            }
        }
        return result;
    }
}
