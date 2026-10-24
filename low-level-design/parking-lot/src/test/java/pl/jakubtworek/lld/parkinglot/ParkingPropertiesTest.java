package pl.jakubtworek.lld.parkinglot;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;
import static pl.jakubtworek.lld.parkinglot.Fixtures.*;

/**
 * Własności uzupełniają konkretne przykłady: dłuższy postój nie obniża ceny,
 * a kolejność deklaracji tych samych miejsc nie zmienia deterministycznego
 * przydziału. To nie benchmark i nie dowód dla dowolnego cennika czy parkingu.
 */
class ParkingPropertiesTest {

    @ParameterizedTest
    @CsvSource({ "0,0", "1,600", "500,0", "500,600", "500,86400", "100000,600" })
    void hourlyAndCappedFeesNeverDecreaseForGrowingDurations(long rate, long graceSeconds) {
        FeePolicy hourly = new HourlyFeePolicy(new Money(rate), Duration.ofSeconds(graceSeconds));
        FeePolicy capped = new CappedFeePolicy(hourly, new Money(1500));
        var random = new Random(1017);
        var entry = Instant.parse("2026-01-01T10:00:00Z");
        var elapsed = Duration.ZERO;
        long previousBase = 0;
        long previousCapped = 0;
        for (int sample = 0; sample < 256; sample++) {
            // Dodajemy także ułamki sekund; próbki są rosnące i powtarzalne.
            elapsed = elapsed.plusSeconds(random.nextInt(7201)).plusNanos(random.nextInt(1_000_000_000));
            var input = new PricingContext(VehicleType.CAR, SpotType.COMPACT, entry, entry.plus(elapsed));
            long baseFee = hourly.calculate(input).grosz();
            long cappedFee = capped.calculate(input).grosz();
            assertTrue(baseFee >= previousBase, "Cena godzinowa nie może maleć przy wydłużaniu postoju");
            assertTrue(cappedFee >= previousCapped, "Limit nie może odwrócić monotoniczności bazowego cennika");
            assertTrue(cappedFee <= 1500 && cappedFee <= baseFee);
            previousBase = baseFee;
            previousCapped = cappedFee;
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "first", "best-fit" })
    void everyPermutationOfDemoConfigurationProducesSameLifecycle(String strategy) {
        var expected = trace(SPOTS, strategy);
        // Dla trzech miejsc sprawdzamy wszystkie 3!=6 permutacji, zamiast
        // losowej próbki. Porównujemy publiczny przebieg i odmowy, nie prywatną
        // posortowaną listę ani UUID niezależnych instancji parkingu.
        for (int first = 0; first < 3; first++) {
            for (int second = 0; second < 3; second++) {
                for (int third = 0; third < 3; third++) {
                    if (first != second && first != third && second != third) {
                        assertEquals(expected, trace(List.of(SPOTS.get(first), SPOTS.get(second), SPOTS.get(third)),
                                strategy));
                    }
                }
            }
        }
    }

    private static List<String> trace(List<ParkingSpot> spots, String strategy) {
        SpotAllocationStrategy allocation = strategy.equals("first") ? new FirstAvailableAllocation() : new BestFitAllocation();
        var parking = new ParkingLot(spots, allocation, hourly(), new TestClock());
        var result = new ArrayList<String>();
        var car = parking.enter(vehicle("CAR", VehicleType.CAR));
        result.add(car.spot().id());
        result.add(parking.enter(vehicle("MOTO", VehicleType.MOTORCYCLE)).spot().id());
        try {
            result.add(parking.enter(vehicle("VAN", VehicleType.VAN)).spot().id());
        } catch (ParkingException error) {
            result.add(error.reason().name());
        }
        parking.exit(car.id());
        result.add(parking.enter(vehicle("CAR", VehicleType.CAR)).spot().id());
        result.add("active=" + parking.snapshot().activeTickets().size());
        result.add("free=" + parking.snapshot().availableSpots().size());
        return result;
    }
}
