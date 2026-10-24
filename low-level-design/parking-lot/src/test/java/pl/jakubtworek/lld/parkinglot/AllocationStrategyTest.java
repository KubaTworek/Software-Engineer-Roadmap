package pl.jakubtworek.lld.parkinglot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static pl.jakubtworek.lld.parkinglot.Fixtures.*;
import static pl.jakubtworek.lld.parkinglot.VehicleType.*;

/**
 * Testy rozszerzenia i granicy zaufania do strategii. Sama wymiana interfejsu
 * nie wystarcza: trzeba pokazać biznesową różnicę oraz odrzucić niepoprawny wynik.
 */
class AllocationStrategyTest {

    @Test
    void preservesLargeSpotForLaterVan() {
        var basic = parking(new FirstAvailableAllocation(), hourly(), new TestClock());
        var extended = parking(new BestFitAllocation(), hourly(), new TestClock());
        assertEquals("L1", basic.enter(vehicle("CAR", CAR)).spot().id());
        assertThrows(ParkingException.class, () -> basic.enter(vehicle("VAN", VAN)));
        assertEquals("C1", extended.enter(vehicle("CAR", CAR)).spot().id());
        assertEquals("L1", extended.enter(vehicle("VAN", VAN)).spot().id());
    }

    @ParameterizedTest
    @CsvSource({ "MOTORCYCLE,MOTORCYCLE,true", "MOTORCYCLE,CAR,false", "MOTORCYCLE,VAN,false",
            "COMPACT,MOTORCYCLE,true", "COMPACT,CAR,true", "COMPACT,VAN,false", "LARGE,MOTORCYCLE,true",
            "LARGE,CAR,true", "LARGE,VAN,true" })
    void compatibilityIsExplicitForEveryPair(SpotType spot, VehicleType vehicle, boolean fits) {
        assertEquals(fits, spot.accommodates(vehicle));
    }

    @Test
    void candidateListIsFilteredSortedAndImmutable() {
        SpotAllocationStrategy inspecting = candidates -> {
            assertEquals(List.of("L1", "C1"), candidates.stream().map(ParkingSpot::id).toList());
            assertThrows(UnsupportedOperationException.class, () -> candidates.clear());
            return candidates.stream().findFirst();
        };
        var parking = parking(inspecting, hourly(), new TestClock());
        assertEquals("L1", parking.enter(vehicle("A", CAR)).spot().id());
    }

    @Test
    void tieBreakIsDeterministicEvenWhenConfigurationOrderIsReversed() {
        var spots = List.of(new ParkingSpot("B", 1, SpotType.COMPACT), new ParkingSpot("A", 1, SpotType.COMPACT));
        for (SpotAllocationStrategy strategy : List.of(new FirstAvailableAllocation(), new BestFitAllocation())) {
            var parking = new ParkingLot(spots, strategy, hourly(), new TestClock());
            assertEquals("A", parking.enter(vehicle("A", CAR)).spot().id());
        }
    }

    @Test
    void inventedOrIncompatibleSpotCannotBeAllocated() {
        SpotAllocationStrategy broken = candidates -> Optional.of(new ParkingSpot("M1", 0, SpotType.MOTORCYCLE));
        var parking = parking(broken, hourly(), new TestClock());
        var before = parking.snapshot();
        assertThrows(IllegalStateException.class, () -> parking.enter(vehicle("A", VAN)));
        assertSame(before, parking.snapshot());
    }

    @Test
    void strategyCannotKeepReturningPreviouslyOccupiedSpot() {
        ParkingSpot[] remembered = new ParkingSpot[1];
        SpotAllocationStrategy broken = candidates -> {
            if (remembered[0] == null) {
                remembered[0] = candidates.getFirst();
            }
            return Optional.of(remembered[0]);
        };
        var parking = parking(broken, hourly(), new TestClock());
        parking.enter(vehicle("A", CAR));
        var before = parking.snapshot();
        assertThrows(IllegalStateException.class, () -> parking.enter(vehicle("B", CAR)));
        assertSame(before, parking.snapshot());
    }

    @Test
    void emptyOrNullResponseDoesNotHideBrokenStrategy() {
        var empty = parking(candidates -> Optional.empty(), hourly(), new TestClock());
        var before = empty.snapshot();
        assertThrows(IllegalStateException.class, () -> empty.enter(vehicle("A", CAR)));
        assertSame(before, empty.snapshot());
        var nullResult = parking(candidates -> null, hourly(), new TestClock());
        var emptyView = nullResult.snapshot();
        assertThrows(NullPointerException.class, () -> nullResult.enter(vehicle("A", CAR)));
        assertSame(emptyView, nullResult.snapshot());
    }
}
