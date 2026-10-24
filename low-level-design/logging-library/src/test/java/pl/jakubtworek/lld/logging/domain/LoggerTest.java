package pl.jakubtworek.lld.logging.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import pl.jakubtworek.lld.logging.format.*;
import pl.jakubtworek.lld.logging.sink.*;
import java.io.IOException;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.Supplier;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static pl.jakubtworek.lld.logging.domain.Delivery.Status.*;

/**
 * Testy kontraktów obserwują wynik, treść oraz kolejność prób, bez prywatnych
 * szczegółów loggera. Progi sprawdzamy pełną macierzą pięciu poziomów.
 */
class LoggerTest {

    static final Instant NOW = Instant.parse("2026-10-23T12:00:00Z");
    static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    static Logger logger(LogLevel threshold, LogFormatter formatter, List<LogTarget> targets, FailurePolicy policy,
            Clock clock) {
        return new Logger("test", threshold, formatter, targets, policy, clock);
    }

    static Stream<Arguments> matrix() {
        var levels = List.of(LogLevel.TRACE, LogLevel.DEBUG, LogLevel.INFO, LogLevel.WARN, LogLevel.ERROR);
        var cases = new ArrayList<Arguments>();
        for (int event = 0; event < 5; event++) for (int threshold = 0; threshold < 5; threshold++) {
            cases.add(Arguments.of(levels.get(event), levels.get(threshold), event >= threshold));
        }
        return cases.stream();
    }

    @ParameterizedTest
    @MethodSource("matrix")
    void thresholdMatrixIsInclusive(LogLevel level, LogLevel threshold, boolean accepted) {
        var memory = new MemoryLogSink();
        var count = new AtomicInteger();
        var logger = logger(threshold, new PlainTextFormatter(), List.of(new LogTarget("memory", LogLevel.TRACE,
                memory)), FailurePolicy.CONTINUE, CLOCK);
        var result = logger.log(level, () -> {
            count.incrementAndGet();
            return "message";
        });
        assertEquals(accepted, result.event().isPresent());
        assertEquals(accepted ? 1 : 0, count.get());
        assertEquals(accepted ? WRITTEN : FILTERED, result.deliveries().getFirst().status());
        assertEquals(accepted ? 1 : 0, memory.records().size());
    }

    @ParameterizedTest
    @EnumSource(LogLevel.class)
    void targetThresholdAlsoApplies(LogLevel targetThreshold) {
        var logger = logger(LogLevel.TRACE, new PlainTextFormatter(), List.of(new LogTarget("sink", targetThreshold,
                text -> {
        })), FailurePolicy.CONTINUE, CLOCK);
        boolean accepted = Set.of(LogLevel.TRACE, LogLevel.DEBUG, LogLevel.INFO, LogLevel.WARN)
                .contains(targetThreshold);
        assertEquals(accepted, logger.log(LogLevel.WARN, "x").event().isPresent());
    }

    @Test
    void allFilteredTargetsSkipSupplierClockAndFormatter() {
        var count = new AtomicInteger();
        Clock forbidden = new Clock() {

            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            public Clock withZone(ZoneId zone) {
                return this;
            }

            public Instant instant() {
                throw new AssertionError("Clock nie powinien być użyty");
            }
        };
        var logger = logger(LogLevel.TRACE, event -> {
            throw new AssertionError("formatter");
        }, List.of(new LogTarget("sink", LogLevel.ERROR, text -> count.incrementAndGet())), FailurePolicy.CONTINUE,
                forbidden);
        var result = logger.log(LogLevel.INFO, () -> {
            throw new AssertionError("supplier");
        });
        assertTrue(result.event().isEmpty());
        assertEquals(0, count.get());
    }

    @Test
    void prepareOnceAndSendSameTextToAllAcceptedTargets() {
        var trace = new ArrayList<String>();
        var first = new MemoryLogSink();
        var second = new MemoryLogSink();
        var captured = new AtomicReference<LogEvent>();
        Clock clock = new Clock() {

            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            public Clock withZone(ZoneId zone) {
                return this;
            }

            public Instant instant() {
                trace.add("clock");
                return NOW;
            }
        };
        var logger = logger(LogLevel.INFO, event -> {
            trace.add("formatter");
            captured.set(event);
            return "formatted";
        }, List.of(new LogTarget("first", LogLevel.INFO, first), new LogTarget("second", LogLevel.DEBUG, second)),
                FailurePolicy.CONTINUE, clock);
        var result = logger.log(LogLevel.INFO, () -> {
            trace.add("supplier");
            return "text";
        });
        assertEquals(List.of("supplier", "clock", "formatter"), trace);
        assertSame(captured.get(), result.event().orElseThrow());
        assertEquals(List.of("formatted"), first.records());
        assertEquals(first.records(), second.records());
    }

    @ParameterizedTest
    @CsvSource({ "CONTINUE,0", "CONTINUE,1", "CONTINUE,2", "STOP_ON_FAILURE,0", "STOP_ON_FAILURE,1",
            "STOP_ON_FAILURE,2" })
    void failurePolicyControlsOnlyLaterAttempts(FailurePolicy policy, int failedIndex) {
        var calls = new ArrayList<Integer>();
        var writes = new ArrayList<Integer>();
        var targets = new ArrayList<LogTarget>();
        for (int index = 0; index < 3; index++) {
            int id = index;
            targets.add(new LogTarget("sink" + id, LogLevel.TRACE, text -> {
                calls.add(id);
                if (id == failedIndex)
                    throw new IOException("failure");
                writes.add(id);
            }));
        }
        var result = logger(LogLevel.TRACE, new PlainTextFormatter(), targets, policy, CLOCK).log(LogLevel.INFO, "x");
        for (int index = 0; index < 3; index++) {
            var expected = index == failedIndex ? FAILED : policy == FailurePolicy.STOP_ON_FAILURE
                    && index > failedIndex ? NOT_ATTEMPTED : WRITTEN;
            assertEquals(expected, result.deliveries().get(index).status());
            assertEquals(expected == FAILED, result.deliveries().get(index).failure().isPresent());
            assertEquals(expected == WRITTEN, writes.contains(index));
            assertEquals(expected != NOT_ATTEMPTED, calls.contains(index));
        }
    }

    @Test
    void filteredAfterFailureIsFilteredRatherThanNotAttempted() {
        var result = logger(LogLevel.TRACE, new PlainTextFormatter(), List.of(new LogTarget("broken", LogLevel.INFO,
                text -> {
            throw new IOException("x");
        }), new LogTarget("filtered", LogLevel.ERROR, text -> fail()), new LogTarget("skipped", LogLevel.INFO,
                text -> fail())), FailurePolicy.STOP_ON_FAILURE, CLOCK).log(LogLevel.INFO, "x");
        assertEquals(List.of(FAILED, FILTERED, NOT_ATTEMPTED), result.deliveries().stream().map(Delivery::status)
                .toList());
    }

    @Test
    void runtimeSinkFailureIsReportedAndErrorPropagates() {
        var next = new MemoryLogSink();
        var result = logger(LogLevel.TRACE, new PlainTextFormatter(), List.of(new LogTarget("broken", LogLevel.TRACE,
                text -> {
            throw new IllegalStateException("bad");
        }), new LogTarget("next", LogLevel.TRACE, next)), FailurePolicy.CONTINUE, CLOCK).log(LogLevel.INFO, "x");
        assertEquals(IllegalStateException.class.getName(), result.deliveries().getFirst().failure().orElseThrow()
                .exceptionType());
        assertEquals(Optional.of("bad"), result.deliveries().getFirst().failure().orElseThrow().message());
        assertEquals(1, next.records().size());
        var logger = logger(LogLevel.TRACE, new PlainTextFormatter(), List.of(new LogTarget("fatal", LogLevel.TRACE,
                text -> {
            throw new AssertionError("fatal");
        }), new LogTarget("next", LogLevel.TRACE, next)), FailurePolicy.CONTINUE, CLOCK);
        assertThrows(AssertionError.class, () -> logger.log(LogLevel.INFO, "x"));
        assertEquals(1, next.records().size());
    }

    @ParameterizedTest
    @ValueSource(strings = { "supplier", "clock", "formatter", "null-supplier-result", "null-formatter-result" })
    void preparationFailureTouchesNoSink(String stage) {
        var memory = new MemoryLogSink();
        Supplier<String> message = () -> {
            if (stage.equals("supplier"))
                throw new IllegalStateException("supplier");
            return stage.equals("null-supplier-result") ? null : "x";
        };
        Clock clock = new Clock() {

            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            public Clock withZone(ZoneId zone) {
                return this;
            }

            public Instant instant() {
                if (stage.equals("clock"))
                    throw new IllegalStateException("clock");
                return NOW;
            }
        };
        LogFormatter formatter = event -> {
            if (stage.equals("formatter"))
                throw new IllegalStateException("formatter");
            return stage.equals("null-formatter-result") ? null : "formatted";
        };
        var logger = logger(LogLevel.INFO, formatter, List.of(new LogTarget("memory", LogLevel.TRACE, memory)),
                FailurePolicy.CONTINUE, clock);
        assertThrows(RuntimeException.class, () -> logger.log(LogLevel.INFO, message));
        assertTrue(memory.records().isEmpty());
    }

    @Test
    void configurationAndResultsAreProtectedButSinkIsSharedByDesign() {
        var memory = new MemoryLogSink();
        var targets = new ArrayList<>(List.of(new LogTarget("memory", LogLevel.TRACE, memory)));
        var logger = logger(LogLevel.INFO, new PlainTextFormatter(), targets, FailurePolicy.CONTINUE, CLOCK);
        targets.clear();
        var result = logger.log(LogLevel.INFO, "one");
        var old = memory.records();
        logger.log(LogLevel.INFO, "two");
        assertEquals(1, old.size());
        assertEquals(2, memory.records().size());
        assertThrows(UnsupportedOperationException.class, () -> old.clear());
        assertThrows(UnsupportedOperationException.class, () -> result.deliveries().clear());
    }

    @Test
    void invalidConfigurationAndInputAreRejectedEvenWhenFiltered() {
        var target = new LogTarget("x", LogLevel.TRACE, text -> {
        });
        assertThrows(IllegalArgumentException.class, () -> logger(LogLevel.INFO, new PlainTextFormatter(), List.of(),
                FailurePolicy.CONTINUE, CLOCK));
        assertThrows(IllegalArgumentException.class, () -> logger(LogLevel.INFO, new PlainTextFormatter(),
                List.of(target, target), FailurePolicy.CONTINUE, CLOCK));
        assertThrows(IllegalArgumentException.class, () -> new Logger(" ", LogLevel.INFO, new PlainTextFormatter(),
                List.of(target), FailurePolicy.CONTINUE, CLOCK));
        assertThrows(NullPointerException.class, () -> logger(null, new PlainTextFormatter(), List.of(target),
                FailurePolicy.CONTINUE, CLOCK));
        var logger = logger(LogLevel.ERROR, new PlainTextFormatter(), List.of(target), FailurePolicy.CONTINUE, CLOCK);
        assertThrows(NullPointerException.class, () -> logger.log(null, "x"));
        assertThrows(NullPointerException.class, () -> logger.log(LogLevel.INFO, (String) null));
        assertThrows(NullPointerException.class, () -> logger.log(LogLevel.INFO, (Supplier<String>) null));
    }

    @Test
    void emptyMessageIsValidAndRepeatedCallIsNotIdempotent() {
        var memory = new MemoryLogSink();
        var logger = logger(LogLevel.INFO, new PlainTextFormatter(), List.of(new LogTarget("m", LogLevel.TRACE,
                memory)), FailurePolicy.CONTINUE, CLOCK);
        logger.log(LogLevel.INFO, "");
        logger.log(LogLevel.INFO, "");
        assertEquals(2, memory.records().size());
        assertEquals(memory.records().getFirst(), memory.records().getLast());
    }
}
