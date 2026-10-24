package pl.jakubtworek.lld.carrental;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static pl.jakubtworek.lld.carrental.Fixtures.*;
import static pl.jakubtworek.lld.carrental.RentalException.Reason.*;

/**
 * Wyrocznia używa zajętych komórek dni, nie wzoru overlaps. 900 poleceń
 * sprawdza rezerwacje, serwis i ich anulowanie. Odbiór i zwrot mają osobne
 * scenariusze: ta próbka nie jest pełnym dowodem cyklu życia wynajmu.
 */
class GeneratedCalendarTest {

    private static final class Entry {

        final int vehicle, from, to;
        final boolean service;
        final UUID token;
        boolean blocking = true;

        Entry(int vehicle, int from, int to, boolean service, UUID token) {
            this.vehicle = vehicle;
            this.from = from;
            this.to = to;
            this.service = service;
            this.token = token;
        }
    }

    // W archiwum zapisujemy kopie oczekiwań; późniejsza zmiana Entry nie może
    // zmienić wyroczni dla starego widoku, inaczej test sam maskowałby błąd.
    private record Archive(RentalSnapshot view, boolean[][] blocked, Map<ReservationId, ReservationStatus> statuses,
            Set<MaintenanceId> serviceIds) {
    }

    @ParameterizedTest
    @ValueSource(longs = { 17, 213, 901 })
    void generatedPlansPreserveCalendarAndHistoricalViews(long seed) {
        var agency = agency(new TestClock());
        var model = new ArrayList<Entry>();
        var archives = new ArrayList<Archive>();
        var random = new Random(seed);
        for (int step = 0; step < 300; step++) {
            int command = random.nextInt(4);
            int car = random.nextInt(2);
            int from = random.nextInt(7);
            int to = from + 1 + random.nextInt(7 - from);
            var before = agency.snapshot();
            if (command < 2) {
                // Enumerujemy komórki, więc także pełne zawieranie przedziałów
                // i sąsiedztwo wynikają z modelu niezależnego od kodu domeny.
                boolean rentalConflict = false;
                boolean serviceConflict = false;
                for (Entry entry : model) {
                    if (!entry.blocking || entry.vehicle != car) {
                        continue;
                    }
                    for (int day = from; day < to; day++) {
                        if (day >= entry.from && day < entry.to) {
                            if (entry.service) {
                                serviceConflict = true;
                            } else {
                                rentalConflict = true;
                            }
                        }
                    }
                }
                var expected = rentalConflict ? RESERVATION_CONFLICT : serviceConflict ? MAINTENANCE_CONFLICT : null;
                VehicleId vehicleId = car == 0 ? V1 : V2;
                if (expected != null) {
                    var error = assertThrows(RentalException.class, () -> {
                        if (command == 0) {
                            agency.reserve(U1, vehicleId, period(from, to));
                        } else {
                            agency.scheduleMaintenance(vehicleId, period(from, to), "Przegląd");
                        }
                    });
                    assertEquals(expected, error.reason());
                    assertSame(before, agency.snapshot());
                } else {
                    UUID token = command == 0 ? agency.reserve(U1, vehicleId, period(from, to)).id()
                            .value() : agency.scheduleMaintenance(vehicleId, period(from, to), "Przegląd").id().value();
                    model.add(new Entry(car, from, to, command == 1, token));
                }
            } else {
                Entry target = model.isEmpty() ? null : model.get(random.nextInt(model.size()));
                UUID token = target == null ? new UUID(0, 0) : target.token;
                var expected = command == 2 ? target == null
                        || target.service ? UNKNOWN_RESERVATION : !target.blocking ? INVALID_STATUS : null : target == null || !target.service || !target.blocking ? UNKNOWN_MAINTENANCE : null;
                Runnable action = command == 2 ? () -> agency.cancel(U1,
                        new ReservationId(token)) : () -> agency.removeMaintenance(new MaintenanceId(token));
                if (expected != null) {
                    assertEquals(expected, assertThrows(RentalException.class, action::run).reason());
                    assertSame(before, agency.snapshot());
                } else {
                    action.run();
                    target.blocking = false;
                }
            }
            boolean[][] blocked = new boolean[2][7];
            var statuses = new HashMap<ReservationId, ReservationStatus>();
            var serviceIds = new HashSet<MaintenanceId>();
            for (Entry entry : model) {
                if (entry.blocking) {
                    for (int day = entry.from; day < entry.to; day++) {
                        blocked[entry.vehicle][day] = true;
                    }
                }
                if (entry.service) {
                    if (entry.blocking) {
                        serviceIds.add(new MaintenanceId(entry.token));
                    }
                } else {
                    statuses.put(new ReservationId(entry.token),
                            entry.blocking ? ReservationStatus.RESERVED : ReservationStatus.CANCELLED);
                }
            }
            var archive = new Archive(agency.snapshot(), blocked, Map.copyOf(statuses), Set.copyOf(serviceIds));
            assertView(archive);
            archives.add(archive);
        }
        archives.forEach(GeneratedCalendarTest::assertView);
    }

    private static void assertView(Archive expected) {
        var view = expected.view();
        assertEquals(expected.statuses().keySet(), view.reservations().keySet());
        expected.statuses().forEach((id, status) -> assertEquals(status, view.reservations().get(id).status()));
        assertEquals(expected.serviceIds(), view.maintenance().keySet());
        for (int car = 0; car < 2; car++) {
            for (int day = 0; day < 7; day++) {
                assertEquals(!expected.blocked()[car][day], view.isAvailable(car == 0 ? V1 : V2, period(day, day + 1)));
            }
        }
    }
}
