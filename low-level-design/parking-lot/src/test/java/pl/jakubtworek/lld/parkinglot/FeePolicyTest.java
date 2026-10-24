package pl.jakubtworek.lld.parkinglot;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import static org.junit.jupiter.api.Assertions.*;
import static pl.jakubtworek.lld.parkinglot.Fixtures.*;
import static pl.jakubtworek.lld.parkinglot.VehicleType.CAR;

/**
 * Granice wyceny wybieramy z kontraktu, a nie z wygodnych pełnych godzin.
 * Jedna nanosekunda za granicą darmowego czasu lub płatnej godziny ma zmienić
 * cenę. Wszystkie przypadki przechodzą przez publiczne enter/exit i sztuczny
 * zegar; test nie czeka i nie zależy od chwili uruchomienia.
 */
class FeePolicyTest {

    @ParameterizedTest
    @CsvSource({ "0,0,0", "599,999999999,0", "600,0,0", "600,1,500", "3599,0,500", "4200,0,500", "4200,1,1000",
            "7800,0,1000", "7800,1,1500" })
    void chargesEachStartedHourAfterSubtractingGracePeriod(long seconds, int nanos, long expectedGrosz) {
        var clock = new TestClock();
        var parking = parking(new BestFitAllocation(), hourly(), clock);
        var ticket = parking.enter(vehicle("A", CAR));
        clock.advance(Duration.ofSeconds(seconds, nanos));
        assertEquals(new Money(expectedGrosz), parking.exit(ticket.id()).fee());
    }

    @Test
    void flatFeeCanReplaceHourlyPolicyWithoutChangingLifecycle() {
        var clock = new TestClock();
        var parking = parking(new BestFitAllocation(), new FlatFeePolicy(new Money(1200)), clock);
        var ticket = parking.enter(vehicle("A", CAR));
        // Ta polityka pobiera stałą należność także dla czasu zerowego.
        assertEquals(new Money(1200), parking.exit(ticket.id()).fee());
        assertEquals(3, parking.snapshot().availableSpots().size());
    }

    @Test
    void elapsedTimeAcrossDstUsesInstantsNotLocalHours() {
        var clock = new TestClock();
        clock.set(Instant.parse("2024-03-31T00:30:00Z"));
        var zone = ZoneId.of("Europe/Warsaw");
        var parking = parking(new BestFitAllocation(), new HourlyFeePolicy(new Money(500), Duration.ZERO),
                clock.withZone(zone));
        var ticket = parking.enter(vehicle("A", CAR));
        assertEquals(1, ticket.enteredAt().atZone(zone).getHour());
        clock.advance(Duration.ofHours(1));
        assertEquals(3, clock.instant().atZone(zone).getHour());
        assertEquals(new Money(500), parking.exit(ticket.id()).fee());
    }

    @Test
    void largeDurationDoesNotRequireConversionToNanos() {
        var clock = new TestClock();
        var parking = parking(new BestFitAllocation(), new HourlyFeePolicy(new Money(1), Duration.ZERO), clock);
        var ticket = parking.enter(vehicle("A", CAR));
        // 400 lat w dobach przekracza zakres Duration.toNanos(), lecz sekundy
        // i należność w groszach mieszczą się w long. To inny przypadek niż
        // prawidłowo odrzucane przepełnienie mnożenia kwoty.
        clock.advance(Duration.ofDays(365L * 400));
        assertEquals(new Money(365L * 400 * 24), parking.exit(ticket.id()).fee());
    }

    @Test
    void negativeGraceIsRejectedAtConstruction() {
        assertThrows(IllegalArgumentException.class, () -> new HourlyFeePolicy(new Money(500), Duration.ofSeconds(-1)));
    }
}
