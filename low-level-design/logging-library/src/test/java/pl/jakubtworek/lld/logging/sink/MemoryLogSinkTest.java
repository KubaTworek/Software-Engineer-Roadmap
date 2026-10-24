package pl.jakubtworek.lld.logging.sink;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import pl.jakubtworek.lld.logging.domain.*;
import pl.jakubtworek.lld.logging.format.PlainTextFormatter;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import java.time.*;

/**
 * Przepełnienie ma obserwowalny kontrakt, nie ciche nieograniczone zużycie pamięci.
 */
class MemoryLogSinkTest {

    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-23T12:00:00Z"), ZoneOffset.UTC);

    @ParameterizedTest
    @ValueSource(ints = { 0, -1, Integer.MIN_VALUE })
    void capacityMustBePositive(int capacity) {
        assertThrows(IllegalArgumentException.class, () -> new MemoryLogSink(capacity, OverflowPolicy.REJECT_NEW));
    }

    @Test
    void rejectKeepsOldRecordAndReportsFailureThroughLogger() throws Exception {
        var memory = new MemoryLogSink(1, OverflowPolicy.REJECT_NEW);
        memory.write("old");
        var old = memory.records();
        assertThrows(SinkCapacityException.class, () -> memory.write("new"));
        assertEquals(old, memory.records());
        assertEquals(1, memory.diagnostics().rejected());
        var next = new MemoryLogSink();
        var logger = new Logger("x", LogLevel.INFO, new PlainTextFormatter(), List.of(new LogTarget("full",
                LogLevel.INFO, memory), new LogTarget("next", LogLevel.INFO, next)), FailurePolicy.STOP_ON_FAILURE,
                CLOCK);
        var result = logger.log(LogLevel.INFO, "x");
        assertEquals(Delivery.Status.FAILED, result.deliveries().getFirst().status());
        assertEquals(SinkCapacityException.class.getName(), result.deliveries().getFirst().failure().orElseThrow()
                .exceptionType());
        assertEquals(Delivery.Status.NOT_ATTEMPTED, result.deliveries().getLast().status());
    }

    @Test
    void dropOldestAcceptsAndPreservesOrderAndOldSnapshots() throws Exception {
        var memory = new MemoryLogSink(2, OverflowPolicy.DROP_OLDEST);
        memory.write("a");
        memory.write("b");
        var old = memory.records();
        memory.write("c");
        assertEquals(List.of("b", "c"), memory.records());
        assertEquals(List.of("a", "b"), old);
        assertEquals(1, memory.diagnostics().dropped());
        assertThrows(UnsupportedOperationException.class, () -> old.clear());
        memory.clear();
        memory.write("d");
        assertEquals(List.of("d"), memory.records());
        assertEquals(1, memory.diagnostics().dropped());
    }

    @Test
    void nullDoesNotDiscardOldestOrIncrementCounters() throws Exception {
        var memory = new MemoryLogSink(1, OverflowPolicy.DROP_OLDEST);
        memory.write("old");
        var before = memory.diagnostics();
        assertThrows(NullPointerException.class, () -> memory.write(null));
        assertEquals(before, memory.diagnostics());
        assertEquals(List.of("old"), memory.records());
    }

    @Test
    void defaultBufferIsBoundedAndRetainsMostRecent() throws Exception {
        var memory = new MemoryLogSink();
        for (int i = 0; i < 1001; i++) memory.write("r" + i);
        assertEquals(1000, memory.records().size());
        assertEquals("r1", memory.records().getFirst());
        assertEquals(1, memory.diagnostics().dropped());
    }
}
