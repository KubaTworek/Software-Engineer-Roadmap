package pl.jakubtworek.lld.parkinglot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static pl.jakubtworek.lld.parkinglot.Fixtures.*;

/**
 * Decorator ma zachować bazową regułę i ograniczyć poprawny wynik. Osobno
 * badamy granice, złożenie, kolejność delegowania i brak ukrywania awarii.
 * Nie zakładamy, że ograniczenie wyniku potrafi naprawić błędne obliczenie.
 */
class CappedFeePolicyTest {

    private static final Instant AT = Instant.parse("2026-01-01T10:00:00Z");

    private static PricingContext context(Duration stay) {
        return new PricingContext(VehicleType.CAR, SpotType.COMPACT, AT, AT.plus(stay));
    }

    @ParameterizedTest
    @CsvSource({ "500,1500,500", "1500,1500,1500", "2500,1500,1500", "500,0,0", "0,1500,0" })
    void capsFlatTariffBelowAtAndAboveLimit(long raw, long maximum, long expected) {
        var policy = new CappedFeePolicy(new FlatFeePolicy(new Money(raw)), new Money(maximum));
        assertEquals(new Money(expected), policy.calculate(context(Duration.ZERO)));
    }

    @ParameterizedTest
    @CsvSource({ "0,0", "10,0", "11,500", "70,500", "130,1000", "190,1500", "191,1500", "3000,1500" })
    void hourlyCapIsPerWholeStayNotPerDay(long minutes, long expected) {
        var policy = new CappedFeePolicy(hourly(), new Money(1500));
        assertEquals(new Money(expected), policy.calculate(context(Duration.ofMinutes(minutes))));
    }

    @Test
    void delegatesOnceWithSameContextBeforeApplyingEvenZeroLimit() {
        var calls = new AtomicInteger();
        var input = context(Duration.ofHours(5));
        FeePolicy delegate = received -> {
            assertSame(input, received);
            calls.incrementAndGet();
            return new Money(2500);
        };
        assertEquals(new Money(0), new CappedFeePolicy(delegate, new Money(0)).calculate(input));
        assertEquals(1, calls.get());
    }

    @Test
    void nestedCapsComposeWithoutSubclassesAndStricterLimitWins() {
        FeePolicy base = new FlatFeePolicy(new Money(2500));
        var first = new CappedFeePolicy(new CappedFeePolicy(base, new Money(2000)), new Money(1500));
        var reversed = new CappedFeePolicy(new CappedFeePolicy(base, new Money(1500)), new Money(2000));
        assertEquals(new Money(1500), first.calculate(context(Duration.ofHours(10))));
        assertEquals(new Money(1500), reversed.calculate(context(Duration.ofHours(10))));
    }

    @Test
    void delegateFailureAndNullAreNotHiddenByZeroLimit() {
        var failure = new IllegalStateException("Awaria cennika");
        FeePolicy broken = context -> {
            throw failure;
        };
        assertSame(failure, assertThrows(IllegalStateException.class, () -> new CappedFeePolicy(broken, new Money(0))
                .calculate(context(Duration.ZERO))));
        assertThrows(NullPointerException.class, () -> new CappedFeePolicy(context -> null, new Money(0))
                .calculate(context(Duration.ZERO)));
    }

    @Test
    void delegateOverflowStillPreservesParkingStateDespiteLowCap() {
        var clock = new TestClock();
        var policy = new CappedFeePolicy(new HourlyFeePolicy(new Money(Long.MAX_VALUE), Duration.ZERO), new Money(1));
        var parking = parking(new BestFitAllocation(), policy, clock);
        var ticket = parking.enter(vehicle("A", VehicleType.CAR));
        var before = parking.snapshot();
        clock.advance(Duration.ofHours(2));
        assertThrows(ArithmeticException.class, () -> parking.exit(ticket.id()));
        assertSame(before, parking.snapshot());
    }

    @Test
    void cappedSettlementIsStoredAndSpotIsReleased() {
        var clock = new TestClock();
        var parking = parking(new BestFitAllocation(), new CappedFeePolicy(hourly(), new Money(1500)), clock);
        var ticket = parking.enter(vehicle("A", VehicleType.CAR));
        clock.advance(Duration.ofHours(50));
        var receipt = parking.exit(ticket.id());
        assertEquals(new Money(1500), receipt.fee());
        assertSame(receipt, parking.snapshot().tickets().get(ticket.id()).receipt().orElseThrow());
        assertEquals(3, parking.snapshot().availableSpots().size());
    }

    @Test
    void missingDelegateOrLimitIsConfigurationError() {
        assertThrows(NullPointerException.class, () -> new CappedFeePolicy(null, new Money(1)));
        assertThrows(NullPointerException.class, () -> new CappedFeePolicy(hourly(), null));
    }
}
