package pl.jakubtworek.lld.parkinglot;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

/**
 * Dane scenariusza, nie alternatywny model domeny. LARGE na parterze oraz
 * COMPACT wyżej celowo rozróżniają dwie strategie; losowy układ mógłby
 * pozostawić obie strategie z takim samym wynikiem i niczego nie wyjaśnić.
 */
final class Fixtures {

    static final List<ParkingSpot> SPOTS = List.of(new ParkingSpot("L1", 0, SpotType.LARGE), new ParkingSpot("M1", 0,
            SpotType.MOTORCYCLE), new ParkingSpot("C1", 1, SpotType.COMPACT));

    static Vehicle vehicle(String plate, VehicleType type) {
        return new Vehicle(new LicensePlate(plate), type);
    }

    static FeePolicy hourly() {
        return new HourlyFeePolicy(new Money(500), Duration.ofMinutes(10));
    }

    static ParkingLot parking(SpotAllocationStrategy strategy, FeePolicy fee, Clock clock) {
        return new ParkingLot(SPOTS, strategy, fee, clock);
    }
}
