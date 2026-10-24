package pl.jakubtworek.lld.carrental;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static pl.jakubtworek.lld.carrental.Fixtures.*;
import static pl.jakubtworek.lld.carrental.RentalException.Reason.*;

/**
 * Model używa indeksów dni i własnych stanów 0=plan,1=wydane,2=oddane,3=anulowane.
 * Nie wywołuje overlaps ani metod przejść Reservation. Każda historia ma krótki
 * wymuszony początek z późnym i wczesnym zwrotem, a potem losowe polecenia.
 * Zapobiega to pozornemu pokryciu, gdy generator przypadkiem nigdy nie wyda auta.
 * 1600 poleceń jest próbką, nie wyczerpującym dowodem dowolnych historii.
 */
class GeneratedLifecycleTest {

    private enum Kind {

        RESERVE,
        SERVICE,
        CANCEL,
        REMOVE,
        PICKUP,
        RETURN,
        RESCHEDULE,
        ADVANCE
    }

    private record Command(Kind kind, int customer, int car, int from, int to, UUID token) {
    }

    private static final UUID ABSENT = new UUID(0, 0);

    private static final class Entry {

        final UUID id;
        final int customer, car;
        final boolean service;
        int from, to, status, returned = -1;
        boolean removed;

        Entry(UUID id, Command command) {
            this.id = id;
            customer = command.customer();
            car = command.car();
            from = command.from();
            to = command.to();
            service = command.kind() == Kind.SERVICE;
        }

        Frozen freeze() {
            return new Frozen(id, customer, car, service, from, to, status, returned, removed);
        }
    }

    private record Frozen(UUID id, int customer, int car, boolean service, int from, int to, int status, int returned,
            boolean removed) {

        boolean blocks() {
            return service ? !removed : status <= 1;
        }
    }

    private record Expected(RentalException.Reason reason, Frozen source) {
    }

    private record Archive(RentalSnapshot view, int today, List<Frozen> entries) {
    }

    @ParameterizedTest
    @ValueSource(longs = { 17, 71, 213, 901 })
    void generatedLifecyclePreservesPlanPhysicalPossessionSettlementAndOldViews(long seed) {
        var clock = new TestClock();
        var agency = agency(clock);
        var model = new LinkedHashMap<UUID, Entry>();
        var archives = new ArrayList<Archive>();
        var random = new Random(seed);
        int today = 0;
        UUID latest = ABSENT;
        int[] successes = new int[Kind.values().length];
        for (int step = 0; step < 400; step++) {
            Command command = step < 12 ? prefix(step, latest) : randomCommand(random, today, model);
            var frozen = model.values().stream().map(Entry::freeze).toList();
            Expected expected = expected(command, today, frozen);
            var before = agency.snapshot();
            Entry target = model.get(command.token());
            if (command.kind() == Kind.ADVANCE) {
                today += command.from();
                clock.today = TODAY.plusDays(today);
                assertSame(before, agency.snapshot(), "Sam upływ czasu nie wykonuje komend domeny");
                successes[command.kind().ordinal()]++;
            } else {
                Runnable action = () -> execute(agency, command);
                if (expected.reason() != null) {
                    var error = assertThrows(RentalException.class, action::run,
                            "seed=" + seed + ", step=" + step + ", " + command);
                    assertEquals(expected.reason(), error.reason());
                    if (expected.source() != null) {
                        var detail = error.conflict().orElseThrow();
                        assertEquals(expected.source().id(), detail.sourceId());
                        assertEquals(period(expected.source().from(), expected.source().to()), detail.period());
                    } else {
                        assertTrue(error.conflict().isEmpty());
                    }
                    assertSame(before, agency.snapshot());
                } else {
                    execute(agency, command);
                    successes[command.kind().ordinal()]++;
                    switch(command.kind()) {
                        case RESERVE ->
                            {
                                var created = agency.snapshot().reservations().keySet().stream()
                                        .filter(id -> !before.reservations().containsKey(id)).findFirst().orElseThrow();
                                latest = created.value();
                                assertFalse(model.containsKey(latest));
                                model.put(latest, new Entry(latest, command));
                            }
                        case SERVICE ->
                            {
                                var created = agency.snapshot().maintenance().keySet().stream()
                                        .filter(id -> !before.maintenance().containsKey(id)).findFirst().orElseThrow();
                                latest = created.value();
                                model.put(latest, new Entry(latest, command));
                            }
                        case CANCEL ->
                            target.status = 3;
                        case REMOVE ->
                            target.removed = true;
                        case PICKUP ->
                            target.status = 1;
                        case RETURN ->
                            {
                                target.status = 2;
                                target.returned = today;
                            }
                        case RESCHEDULE ->
                            {
                                target.from = command.from();
                                target.to = command.to();
                            }
                        default ->
                            throw new AssertionError();
                    }
                }
            }
            var archive = new Archive(agency.snapshot(), today, model.values().stream().map(Entry::freeze).toList());
            assertView(archive);
            archives.add(archive);
        }
        for (int count : successes) {
            assertTrue(count > 0, "Każdy rodzaj operacji musi mieć co najmniej jeden sukces");
        }
        archives.forEach(GeneratedLifecycleTest::assertView);
    }

    private static void execute(RentalAgency agency, Command command) {
        var customer = command.customer() == 0 ? U1 : U2;
        var vehicle = command.car() == 0 ? V1 : V2;
        var id = new ReservationId(command.token());
        switch(command.kind()) {
            case RESERVE ->
                agency.reserve(customer, vehicle, period(command.from(), command.to()));
            case SERVICE ->
                agency.scheduleMaintenance(vehicle, period(command.from(), command.to()), "Przegląd");
            case CANCEL ->
                agency.cancel(customer, id);
            case REMOVE ->
                agency.removeMaintenance(new MaintenanceId(command.token()));
            case PICKUP ->
                agency.pickup(customer, id);
            case RETURN ->
                agency.returnVehicle(customer, id);
            case RESCHEDULE ->
                agency.reschedule(customer, id, period(command.from(), command.to()));
            default ->
                throw new AssertionError();
        }
    }

    private static Expected expected(Command command, int today, List<Frozen> entries) {
        if (command.kind() == Kind.ADVANCE) {
            return new Expected(null, null);
        }
        Frozen target = entries.stream().filter(entry -> entry.id().equals(command.token())).findFirst().orElse(null);
        if (command.kind() == Kind.REMOVE) {
            return new Expected(target == null || !target.service() || target.removed() ? UNKNOWN_MAINTENANCE : null,
                    null);
        }
        if (command.kind() != Kind.RESERVE && command.kind() != Kind.SERVICE) {
            if (target == null || target.service()) {
                return new Expected(UNKNOWN_RESERVATION, null);
            }
            if (target.customer() != command.customer()) {
                return new Expected(NOT_CUSTOMER, null);
            }
            int required = command.kind() == Kind.RETURN ? 1 : 0;
            if (target.status() != required) {
                return new Expected(INVALID_STATUS, null);
            }
            if (command.kind() == Kind.CANCEL || command.kind() == Kind.RETURN) {
                return new Expected(null, null);
            }
            if (command.kind() == Kind.PICKUP) {
                if (entries.stream().anyMatch(entry -> !entry.service() && entry.car() == target.car()
                        && entry.status() == 1)) {
                    return new Expected(VEHICLE_NOT_RETURNED, null);
                }
                return new Expected(target.from() != today ? PICKUP_DATE_MISMATCH : null, null);
            }
        }
        if (command.from() < today) {
            return new Expected(PAST_PERIOD, null);
        }
        int car = command.kind() == Kind.RESCHEDULE ? target.car() : command.car();
        Frozen best = null;
        for (Frozen entry : entries) {
            if (!entry.blocks() || entry.car() != car || (command.kind() == Kind.RESCHEDULE && entry.id()
                    .equals(command.token()))) {
                continue;
            }
            boolean intersects = false;
            for (int day = command.from(); day < command.to(); day++) {
                intersects |= day >= entry.from() && day < entry.to();
            }
            if (!intersects) {
                continue;
            }
            // Priorytet rodzaju i minimum wybieramy z modelu komórek dni,
            // bez kopiowania predykatu overlaps czy wywoływania produkcyjnej kwerendy.
            if (best == null || (best.service() && !entry.service()) || (best.service() == entry.service()
                    && (entry.from() < best.from() || (entry.from() == best.from() && entry.id()
                    .compareTo(best.id()) < 0)))) {
                best = entry;
            }
        }
        return new Expected(best == null ? null : best.service() ? MAINTENANCE_CONFLICT : RESERVATION_CONFLICT, best);
    }

    private static Command randomCommand(Random random, int today, Map<UUID, Entry> model) {
        Kind kind = Kind.values()[random.nextInt(Kind.values().length)];
        var candidates = model.values().stream().filter(entry -> switch(kind) {
            case PICKUP ->
                !entry.service && entry.status == 0 && entry.from == today;
            case RETURN ->
                !entry.service && entry.status == 1;
            case RESCHEDULE, CANCEL ->
                !entry.service && entry.status == 0;
            case REMOVE ->
                entry.service && !entry.removed;
            default ->
                true;
        }).toList();
        if (candidates.isEmpty()) {
            candidates = List.copyOf(model.values());
        }
        Entry selected = candidates.isEmpty() ? null : candidates.get(random.nextInt(candidates.size()));
        int from = today + random.nextInt(5) - 1;
        int customer = selected != null && random.nextInt(4) != 0 ? selected.customer : random.nextInt(2);
        return new Command(kind, customer, random.nextInt(2), kind == Kind.ADVANCE ? random.nextInt(3) : from,
                from + 1 + random.nextInt(4), selected == null ? ABSENT : selected.id);
    }

    private static Command prefix(int step, UUID latest) {
        return switch(step) {
            case 0 ->
                new Command(Kind.RESERVE, 0, 0, 0, 2, ABSENT);
            case 1, 6 ->
                new Command(Kind.PICKUP, 0, 0, 0, 0, latest);
            case 2 ->
                new Command(Kind.ADVANCE, 0, 0, 3, 0, ABSENT);
            case 3, 7 ->
                new Command(Kind.RETURN, 0, 0, 0, 0, latest);
            case 4 ->
                new Command(Kind.RESERVE, 0, 0, 3, 6, ABSENT);
            case 5 ->
                new Command(Kind.RESCHEDULE, 0, 0, 3, 5, latest);
            case 8 ->
                new Command(Kind.SERVICE, 0, 0, 4, 6, ABSENT);
            case 9 ->
                new Command(Kind.REMOVE, 0, 0, 0, 0, latest);
            case 10 ->
                new Command(Kind.RESERVE, 1, 1, 3, 4, ABSENT);
            case 11 ->
                new Command(Kind.CANCEL, 1, 1, 0, 0, latest);
            default ->
                throw new AssertionError();
        };
    }

    private static void assertView(Archive expected) {
        var view = expected.view();
        long rentals = expected.entries().stream().filter(entry -> !entry.service()).count();
        long services = expected.entries().stream().filter(entry -> entry.service() && !entry.removed()).count();
        assertEquals(rentals, view.reservations().size());
        assertEquals(services, view.maintenance().size());
        int[] physicallyOut = new int[2];
        for (Frozen entry : expected.entries()) {
            if (entry.service()) {
                if (!entry.removed()) {
                    var block = view.maintenance().get(new MaintenanceId(entry.id()));
                    assertEquals(period(entry.from(), entry.to()), block.period());
                    assertEquals(entry.car() == 0 ? V1 : V2, block.vehicleId());
                }
                continue;
            }
            var actual = view.reservations().get(new ReservationId(entry.id()));
            var expectedStatus = switch(entry.status()) {
                case 0 ->
                    ReservationStatus.RESERVED;
                case 1 ->
                    ReservationStatus.ACTIVE;
                case 2 ->
                    ReservationStatus.COMPLETED;
                case 3 ->
                    ReservationStatus.CANCELLED;
                default ->
                    throw new AssertionError();
            };
            assertEquals(expectedStatus, actual.status());
            assertEquals(entry.customer() == 0 ? U1 : U2, actual.customerId());
            assertEquals(entry.car() == 0 ? V1 : V2, actual.vehicleId());
            assertEquals(period(entry.from(), entry.to()), actual.period());
            long rate = entry.car() == 0 ? 10000 : 20000;
            assertEquals(new Money(rate), actual.dailyRate());
            assertEquals(new Money((entry.to() - entry.from()) * rate), actual.quotedTotal());
            if (entry.status() == 1) {
                physicallyOut[entry.car()]++;
            }
            if (entry.status() == 2) {
                var receipt = actual.receipt().orElseThrow();
                assertEquals(actual.id(), receipt.reservationId());
                assertEquals(TODAY.plusDays(entry.returned()), receipt.returnedOn());
                assertEquals(actual.quotedTotal(), receipt.plannedCharge());
                assertEquals(new Money(Math.max(0, entry.returned() - entry.to()) * rate), receipt.lateCharge());
                assertEquals(new Money((entry.to() - entry.from() + Math.max(0, entry.returned() - entry.to())) * rate),
                        receipt.total());
            } else {
                assertTrue(actual.receipt().isEmpty());
            }
        }
        for (int car = 0; car < 2; car++) {
            assertTrue(physicallyOut[car] <= 1, "Auto nie może być fizycznie wydane dwóm klientom");
            for (int day = expected.today() - 1; day < expected.today() + 6; day++) {
                int blocked = 0;
                for (Frozen entry : expected.entries()) {
                    if (entry.car() == car && entry.blocks() && day >= entry.from() && day < entry.to()) {
                        blocked++;
                    }
                }
                assertTrue(blocked <= 1, "Plany umów i serwisu nie mogą nakładać się na auto");
                assertEquals(blocked == 0, view.isAvailable(car == 0 ? V1 : V2, period(day, day + 1)));
            }
        }
    }
}
