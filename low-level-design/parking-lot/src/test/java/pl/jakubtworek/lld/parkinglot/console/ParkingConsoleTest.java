package pl.jakubtworek.lld.parkinglot.console;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import pl.jakubtworek.lld.parkinglot.BestFitAllocation;
import pl.jakubtworek.lld.parkinglot.CappedFeePolicy;
import pl.jakubtworek.lld.parkinglot.HourlyFeePolicy;
import pl.jakubtworek.lld.parkinglot.Money;
import pl.jakubtworek.lld.parkinglot.ParkingLot;
import pl.jakubtworek.lld.parkinglot.ParkingSpot;
import pl.jakubtworek.lld.parkinglot.SpotType;
import pl.jakubtworek.lld.parkinglot.Vehicle;
import pl.jakubtworek.lld.parkinglot.VehicleType;
import pl.jakubtworek.lld.parkinglot.LicensePlate;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Prawdziwa domena i adapter, lecz wejście/wyjście w pamięci. Test pokazuje
 * sekwencję poleceń użytkownika, nie dowodzi działania dowolnego terminala.
 * DemoClock pozwala obserwować naliczanie opłat bez opóźnień i bez Thread.sleep.
 */
class ParkingConsoleTest {

    @Test
    void fullConsoleJourneyHandlesErrorsAndKeepsHistoricalSettlement() throws IOException {
        var clock = new DemoClock(Instant.parse("2026-01-01T10:00:00Z"));
        var parking = parking(clock);
        String output = run(parking, clock,
                "help\nenter CAR WX1\nenter VAN WX2\nenter CAR WX1\n" + "exit 99\nadvance 70\nexit 1\nexit 1\nstatus\nq\n");
        assertTrue(output.contains("miejsce C1"));
        assertTrue(output.contains("[VEHICLE_ALREADY_PARKED]"));
        assertTrue(output.contains("[UNKNOWN_TICKET]"));
        assertTrue(output.contains("należność 5.00 PLN"));
        assertTrue(output.contains("[TICKET_ALREADY_CLOSED]"));
        assertTrue(output.contains("CLOSED 5.00 PLN"));
        assertEquals(1, parking.snapshot().activeTickets().size());
    }

    @Test
    void badTextAndBackwardDemoTimeDoNotCreateTickets() throws IOException {
        var clock = new DemoClock(Instant.parse("2026-01-01T10:00:00Z"));
        var parking = parking(clock);
        var before = parking.snapshot();
        var at = clock.instant();
        String output = run(parking, clock,
                "\nwhat\nenter CAR\nenter PLANE A\nexit abc\nexit 0\n" + "advance -1\nadvance 9999999999999999999\nadvance 999999999999999\nq\n");
        assertTrue(output.contains("Nieznane polecenie"));
        assertTrue(output.contains("Niepoprawne polecenie lub wartość"));
        assertSame(before, parking.snapshot());
        assertEquals(at, clock.instant());
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "q\n", " Q \n" })
    void eofAndQuitDoNotCloseActiveStay(String input) throws IOException {
        var clock = new DemoClock(Instant.parse("2026-01-01T10:00:00Z"));
        var parking = parking(clock);
        parking.enter(new Vehicle(new LicensePlate("WX1"), VehicleType.CAR));
        var before = parking.snapshot();
        assertTrue(run(parking, clock, input).contains("Koniec demo"));
        assertSame(before, parking.snapshot());
        assertEquals(1, parking.snapshot().activeTickets().size());
    }

    @Test
    void demoClockZoneViewsShareTimeAndOverflowDoesNotMoveIt() {
        var clock = new DemoClock(Instant.parse("2026-01-01T10:00:00Z"));
        var view = clock.withZone(ZoneId.of("Europe/Warsaw"));
        clock.advance(Duration.ofMinutes(1));
        assertEquals(clock.instant(), view.instant());
        assertEquals(ZoneId.of("Europe/Warsaw"), view.getZone());
        var before = clock.instant();
        assertThrows(RuntimeException.class, () -> clock.advance(Duration.ofSeconds(Long.MAX_VALUE)));
        assertEquals(before, clock.instant());
    }

    @Test
    void consoleReportsAndStoresCappedSettlement() throws IOException {
        var clock = new DemoClock(Instant.parse("2026-01-01T10:00:00Z"));
        var parking = new ParkingLot(List.of(new ParkingSpot("C1", 0, SpotType.COMPACT)), new BestFitAllocation(),
                new CappedFeePolicy(new HourlyFeePolicy(new Money(500), Duration.ofMinutes(10)), new Money(1500)),
                clock);
        String output = run(parking, clock, "enter CAR WX1\nadvance 3000\nexit 1\nstatus\nq\n");
        assertTrue(output.contains("należność 15.00 PLN"));
        assertTrue(output.contains("CLOSED 15.00 PLN"));
        assertTrue(parking.snapshot().activeTickets().isEmpty());
        assertEquals(1, parking.snapshot().availableSpots().size());
    }

    private static ParkingLot parking(DemoClock clock) {
        return new ParkingLot(List.of(new ParkingSpot("L1", 0, SpotType.LARGE), new ParkingSpot("C1", 1,
                SpotType.COMPACT)), new BestFitAllocation(), new HourlyFeePolicy(new Money(500),
                Duration.ofMinutes(10)), clock);
    }

    private static String run(ParkingLot parking, DemoClock clock, String commands) throws IOException {
        var output = new StringWriter();
        new ParkingConsole(parking, clock, new StringReader(commands), new PrintWriter(output)).run();
        return output.toString();
    }
}
