package pl.jakubtworek.lld.carrental;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Stałe dane i sterowany zegar pozwalają testować przekazanie auta bez czekania na prawdziwy dzień.
 */
public final class Fixtures {

    public static final LocalDate TODAY = LocalDate.of(2026, 10, 19);
    public static final VehicleId V1 = new VehicleId("V1");
    public static final VehicleId V2 = new VehicleId("V2");
    public static final CustomerId U1 = new CustomerId("U1");
    public static final CustomerId U2 = new CustomerId("U2");

    private Fixtures() {
    }

    public static RentalPeriod period(int from, int to) {
        return new RentalPeriod(TODAY.plusDays(from), TODAY.plusDays(to));
    }

    public static RentalAgency agency(Clock clock) {
        return new RentalAgency(List.of(new Vehicle(V1, "Kompakt", VehicleCategory.ECONOMY), new Vehicle(V2, "SUV",
                VehicleCategory.SUV)), List.of(new Customer(U1, "Ala"), new Customer(U2, "Bartek")), clock);
    }

    public static final class TestClock extends Clock {

        public LocalDate today = TODAY;
        public RuntimeException failure;
        public int reads;

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            java.util.Objects.requireNonNull(zone, "zone");
            return new Clock() {

                @Override
                public ZoneId getZone() {
                    return zone;
                }

                @Override
                public Clock withZone(ZoneId other) {
                    return TestClock.this.withZone(other);
                }

                @Override
                public Instant instant() {
                    return TestClock.this.instant();
                }
            };
        }

        @Override
        public Instant instant() {
            reads++;
            if (failure != null) {
                throw failure;
            }
            return today.atStartOfDay(ZoneOffset.UTC).toInstant();
        }
    }
}
