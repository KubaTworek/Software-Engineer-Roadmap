package pl.jakubtworek.lld.parkinglot;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static pl.jakubtworek.lld.parkinglot.Fixtures.*;

/**
 * Nowy kontrakt wyceny można badać bez tworzenia biletu. Test integracyjny
 * sprawdza osobno, że parking rzeczywiście przekazuje dane danego postoju.
 * To rozdziela poprawność wartości wejściowej od poprawności jej użycia.
 */
class PricingContextTest {

    private static final Instant AT = Instant.parse("2026-01-01T10:00:00Z");

    @Test
    void invalidIntervalIsRejectedBeforeEitherTariffCanUseIt() {
        assertThrows(IllegalArgumentException.class, () -> new PricingContext(VehicleType.CAR, SpotType.COMPACT, AT,
                AT.minusNanos(1)));
        var zero = new PricingContext(VehicleType.CAR, SpotType.COMPACT, AT, AT);
        assertEquals(Duration.ZERO, zero.duration());
        assertEquals(new Money(0), hourly().calculate(zero));
        assertEquals(new Money(1200), new FlatFeePolicy(new Money(1200)).calculate(zero));
    }

    @Test
    void requiredFieldsAndNullContextAreRejectedConsistently() {
        assertThrows(NullPointerException.class, () -> new PricingContext(null, SpotType.LARGE, AT, AT));
        assertThrows(NullPointerException.class, () -> new PricingContext(VehicleType.CAR, null, AT, AT));
        assertThrows(NullPointerException.class, () -> new PricingContext(VehicleType.CAR, SpotType.LARGE, null, AT));
        assertThrows(NullPointerException.class, () -> new PricingContext(VehicleType.CAR, SpotType.LARGE, AT, null));
        for (FeePolicy policy : List.of(hourly(), new FlatFeePolicy(new Money(1200)), new CappedFeePolicy(hourly(),
                new Money(1500)))) {
            assertThrows(NullPointerException.class, () -> policy.calculate(null));
        }
    }

    @Test
    void parkingPassesOnlyRelevantContextToPolicy() {
        var clock = new TestClock();
        FeePolicy inspecting = context -> {
            assertEquals(VehicleType.VAN, context.vehicleType());
            assertEquals(SpotType.LARGE, context.spotType());
            assertEquals(AT, context.enteredAt());
            assertEquals(AT.plusSeconds(660), context.exitedAt());
            assertEquals(Duration.ofMinutes(11), context.duration());
            return new Money(777);
        };
        var parking = parking(new BestFitAllocation(), inspecting, clock);
        var ticket = parking.enter(vehicle("VAN1", VehicleType.VAN));
        clock.advance(Duration.ofMinutes(11));
        assertEquals(new Money(777), parking.exit(ticket.id()).fee());
    }
}
