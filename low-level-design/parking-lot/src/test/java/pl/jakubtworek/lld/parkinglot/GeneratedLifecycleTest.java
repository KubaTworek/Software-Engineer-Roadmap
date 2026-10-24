package pl.jakubtworek.lld.parkinglot;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;
import static pl.jakubtworek.lld.parkinglot.Fixtures.*;

/**
 * Próbkowanie 2400 poleceń z niezależnym modelem tabelarycznym. Model nie
 * wywołuje accommodates ani FeePolicy, żeby nie kopiować badanego błędu.
 * Stałe ziarno pozwala odtworzyć błąd. To szersza próbka historii, nie dowód
 * dla wszystkich przebiegów ani test współbieżności.
 */
class GeneratedLifecycleTest {

    private record Stay(String plate, VehicleType type, ParkingSpot spot, Instant enteredAt, boolean active, long fee) {

        Stay closed(long amount) {
            return new Stay(plate, type, spot, enteredAt, false, amount);
        }
    }

    private record Archive(ParkingSnapshot snapshot, Set<String> activePlates) {
    }

    @ParameterizedTest
    @CsvSource({ "first,hourly", "first,flat", "best-fit,hourly", "best-fit,flat", "first,capped-hourly",
            "first,capped-flat", "best-fit,capped-hourly", "best-fit,capped-flat" })
    void generatedCommandsAgreeWithReferenceAndKeepArchivedViewsStable(String strategy, String tariff) {
        var clock = new TestClock();
        var allocation = strategy.equals("first") ? new FirstAvailableAllocation() : new BestFitAllocation();
        FeePolicy fees = tariff.endsWith("hourly") ? hourly() : new FlatFeePolicy(new Money(1200));
        if (tariff.startsWith("capped-")) {
            fees = new CappedFeePolicy(fees, new Money(800));
        }
        var parking = parking(allocation, fees, clock);
        var reference = new HashMap<Long, Stay>();
        var archives = new ArrayList<Archive>();
        var random = new Random(7142);
        for (int step = 0; step < 300; step++) {
            clock.advance(Duration.ofSeconds(random.nextInt(1500)));
            var before = parking.snapshot();
            if (random.nextBoolean()) {
                String plate = "P" + random.nextInt(7);
                VehicleType type = VehicleType.values()[random.nextInt(3)];
                boolean duplicate = reference.values().stream().anyMatch(stay -> stay.active() && stay.plate()
                        .equals(plate));
                Set<String> occupied = reference.values().stream().filter(Stay::active).map(stay -> stay.spot().id())
                        .collect(Collectors.toSet());
                Comparator<ParkingSpot> order = Comparator.comparingInt(ParkingSpot::floor)
                        .thenComparing(ParkingSpot::id);
                if (strategy.equals("best-fit")) {
                    order = Comparator.comparingInt((ParkingSpot spot) -> switch(spot.type()) {
                        case MOTORCYCLE ->
                            0;
                        case COMPACT ->
                            1;
                        case LARGE ->
                            2;
                    }).thenComparing(order);
                }
                var expected = SPOTS.stream().filter(spot -> !occupied.contains(spot.id()))
                        .filter(spot -> fits(spot.type(), type)).min(order);
                if (duplicate || expected.isEmpty()) {
                    var error = assertThrows(ParkingException.class, () -> parking.enter(vehicle(plate, type)));
                    assertEquals(duplicate ? ParkingException.Reason.VEHICLE_ALREADY_PARKED : ParkingException.Reason.NO_COMPATIBLE_SPOT, error.reason());
                    assertSame(before, parking.snapshot());
                } else {
                    var ticket = parking.enter(vehicle(plate, type));
                    long number = reference.size() + 1L;
                    assertEquals(number, ticket.id().number());
                    assertEquals(expected.orElseThrow(), ticket.spot());
                    reference.put(number, new Stay(plate, type, expected.orElseThrow(), clock.instant(), true, -1));
                }
            } else {
                long number = random.nextInt(reference.size() + 3) + 1L;
                Stay stay = reference.get(number);
                var id = new TicketId(parking.id(), number);
                if (stay == null || !stay.active()) {
                    var error = assertThrows(ParkingException.class, () -> parking.exit(id));
                    assertEquals(stay == null ? ParkingException.Reason.UNKNOWN_TICKET : ParkingException.Reason.TICKET_ALREADY_CLOSED, error.reason());
                    assertSame(before, parking.snapshot());
                } else {
                    // Generator używa pełnych sekund. Nanosekundy i granice
                    // darmowego czasu sprawdza osobno FeePolicyTest.
                    long seconds = Math.max(0, Duration.between(stay.enteredAt(), clock.instant()).getSeconds() - 600);
                    long fee = tariff.endsWith("flat") ? 1200 : ((seconds + 3599) / 3600) * 500;
                    if (tariff.startsWith("capped-")) {
                        fee = Math.min(fee, 800);
                    }
                    assertEquals(new Money(fee), parking.exit(id).fee());
                    reference.put(number, stay.closed(fee));
                }
            }
            assertMatches(reference, parking.snapshot());
            archives.add(new Archive(parking.snapshot(), activePlates(reference)));
        }
        // Starsze mapy i bilety nie mogą zmienić aktywności po późniejszym exit.
        for (Archive archive : archives) {
            assertEquals(archive.activePlates(), archive.snapshot().activeTickets().stream()
                    .map(ticket -> ticket.vehicle().plate().value()).collect(Collectors.toSet()));
        }
    }

    private static boolean fits(SpotType spot, VehicleType vehicle) {
        return switch(vehicle) {
            case MOTORCYCLE ->
                true;
            case CAR ->
                spot == SpotType.COMPACT || spot == SpotType.LARGE;
            case VAN ->
                spot == SpotType.LARGE;
        };
    }

    private static Set<String> activePlates(Map<Long, Stay> reference) {
        return reference.values().stream().filter(Stay::active).map(Stay::plate).collect(Collectors.toSet());
    }

    private static void assertMatches(Map<Long, Stay> reference, ParkingSnapshot actual) {
        assertEquals(reference.size(), actual.tickets().size());
        var occupied = new HashSet<String>();
        for (var ticket : actual.tickets().values()) {
            Stay expected = reference.get(ticket.id().number());
            assertEquals(expected.active(), ticket.isActive());
            assertEquals(expected.plate(), ticket.vehicle().plate().value());
            assertEquals(expected.type(), ticket.vehicle().type());
            assertEquals(expected.spot(), ticket.spot());
            assertEquals(expected.enteredAt(), ticket.enteredAt());
            if (ticket.isActive()) {
                assertTrue(occupied.add(ticket.spot().id()),
                        "Dwa aktywne bilety nie mogą zajmować tego samego miejsca");
            } else {
                assertEquals(new Money(expected.fee()), ticket.receipt().orElseThrow().fee());
            }
        }
        assertEquals(SPOTS.size() - occupied.size(), actual.availableSpots().size());
        assertEquals(activePlates(reference), actual.activeTickets().stream().map(ticket -> ticket.vehicle().plate()
                .value()).collect(Collectors.toSet()));
    }
}
