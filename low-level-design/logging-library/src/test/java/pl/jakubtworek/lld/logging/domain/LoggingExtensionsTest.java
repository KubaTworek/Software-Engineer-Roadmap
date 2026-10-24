package pl.jakubtworek.lld.logging.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import pl.jakubtworek.lld.logging.format.*;
import pl.jakubtworek.lld.logging.sink.*;
import java.io.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static pl.jakubtworek.lld.logging.domain.Delivery.Status.*;
import static pl.jakubtworek.lld.logging.domain.LoggerTest.CLOCK;

/**
 * Dowody przyrostu: zagnieżdżenie odrzucone przed filtrowaniem, finally po błędzie
 * oraz brak oryginalnego pola w zdarzeniu wynikowym po maskowaniu.
 */
class LoggingExtensionsTest {

    @ParameterizedTest
    @CsvSource({ "supplier,CONTINUE", "clock,CONTINUE", "masker,CONTINUE", "formatter,CONTINUE", "sink,CONTINUE",
            "supplier,STOP_ON_FAILURE", "clock,STOP_ON_FAILURE", "masker,STOP_ON_FAILURE", "formatter,STOP_ON_FAILURE",
            "sink,STOP_ON_FAILURE" })
    void recursionIsRejectedAndLaterCallWorks(String stage, FailurePolicy policy) {
        var reference = new AtomicReference<Logger>();
        var once = new AtomicBoolean(true);
        Runnable reenter = () -> reference.get().log(LogLevel.TRACE, "nested-filtered");
        var memory = new MemoryLogSink();
        Clock clock = new Clock() {

            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            public Clock withZone(ZoneId zone) {
                return this;
            }

            public Instant instant() {
                if (stage.equals("clock") && once.getAndSet(false))
                    reenter.run();
                return CLOCK.instant();
            }
        };
        LogFormatter formatter = event -> {
            if (stage.equals("formatter") && once.getAndSet(false))
                reenter.run();
            return event.message();
        };
        ContextMasker masker = context -> {
            if (stage.equals("masker") && once.getAndSet(false))
                reenter.run();
            return context;
        };
        LogSink recursive = text -> {
            if (stage.equals("sink") && once.getAndSet(false))
                reenter.run();
        };
        var logger = new Logger("test", LogLevel.INFO, formatter, List.of(new LogTarget("recursive", LogLevel.INFO,
                recursive), new LogTarget("memory", LogLevel.INFO, memory)), policy, clock, masker);
        reference.set(logger);
        if (stage.equals("sink")) {
            var result = logger.log(LogLevel.INFO, "outer");
            assertEquals(FAILED, result.deliveries().getFirst().status());
            assertEquals(ReentrantLoggingException.class.getName(), result.deliveries().getFirst().failure()
                    .orElseThrow().exceptionType());
            assertEquals(policy == FailurePolicy.CONTINUE ? WRITTEN : NOT_ATTEMPTED, result.deliveries().getLast()
                    .status());
        } else {
            assertThrows(ReentrantLoggingException.class, () -> logger.log(LogLevel.INFO, () -> {
                if (stage.equals("supplier") && once.getAndSet(false))
                    reenter.run();
                return "outer";
            }));
            assertTrue(memory.records().isEmpty());
        }
        assertEquals(WRITTEN, logger.log(LogLevel.INFO, "after").deliveries().getLast().status());
        assertTrue(memory.records().contains("after"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "supplier", "clock", "masker", "formatter", "sink" })
    void errorAlsoReleasesGuard(String stage) {
        var once = new AtomicBoolean(true);
        Runnable error = () -> {
            if (once.getAndSet(false))
                throw new AssertionError("fatal");
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
                    error.run();
                return CLOCK.instant();
            }
        };
        var logger = new Logger("x", LogLevel.INFO, event -> {
            if (stage.equals("formatter"))
                error.run();
            return event.message();
        }, List.of(new LogTarget("sink", LogLevel.INFO, text -> {
            if (stage.equals("sink"))
                error.run();
        })), FailurePolicy.CONTINUE, clock, context -> {
            if (stage.equals("masker"))
                error.run();
            return context;
        });
        assertThrows(AssertionError.class, () -> logger.log(LogLevel.INFO, () -> {
            if (stage.equals("supplier"))
                error.run();
            return "x";
        }));
        assertEquals(WRITTEN, logger.log(LogLevel.INFO, "after").deliveries().getFirst().status());
    }

    @Test
    void contextCopiesValidatesSortsAndDoesNotLeakBetweenCalls() {
        var source = new HashMap<>(Map.of("z", "last", "a", "first"));
        var context = new LogContext(source);
        source.clear();
        assertEquals(List.of("a", "z"), new ArrayList<>(context.fields().keySet()));
        assertThrows(UnsupportedOperationException.class, () -> context.fields().clear());
        assertThrows(IllegalArgumentException.class, () -> new LogContext(Map.of("bad key", "x")));
        assertThrows(IllegalArgumentException.class, () -> new LogContext(Map.of("bad\nkey", "x")));
        var invalid = new HashMap<String, String>();
        invalid.put("a", null);
        assertThrows(NullPointerException.class, () -> new LogContext(invalid));
        var logger = new Logger("x", LogLevel.INFO, new JsonLogFormatter(), List.of(new LogTarget("m", LogLevel.INFO,
                new MemoryLogSink())), FailurePolicy.CONTINUE, CLOCK);
        assertEquals(context, logger.log(LogLevel.INFO, context, "x").event().orElseThrow().context());
        assertTrue(logger.log(LogLevel.INFO, "next").event().orElseThrow().context().fields().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void maskingProtectsResultAndAllFormattedTargetsWithoutMutatingInput(boolean json) {
        var keys = new HashSet<>(Set.of("password"));
        var masker = ContextMasker.redact(keys, "***");
        keys.clear();
        var context = new LogContext(Map.of("password", "raw-secret", "requestId", "r1", "orderId", "o1"));
        var first = new MemoryLogSink();
        var second = new MemoryLogSink();
        var logger = new Logger("test", LogLevel.INFO, json ? new JsonLogFormatter() : new PlainTextFormatter(),
                List.of(new LogTarget("first", LogLevel.TRACE, first), new LogTarget("second", LogLevel.TRACE, second)),
                FailurePolicy.CONTINUE, CLOCK, masker);
        var result = logger.log(LogLevel.INFO, context, "accepted");
        assertEquals("raw-secret", context.fields().get("password"));
        assertEquals("***", result.event().orElseThrow().context().fields().get("password"));
        assertEquals(first.records(), second.records());
        assertFalse(first.records().getFirst().contains("raw-secret"));
        assertTrue(first.records().getFirst().contains("r1"));
    }

    @Test
    void contextEscapingAndOrderingAreDeterministicAndFieldsAreNestedInJson() {
        var context = new LogContext(Map.of("z", "\"\n\\", "timestamp", "field", "a", "first"));
        var event = new LogEvent(Instant.EPOCH, LogLevel.INFO, "x", "message", context);
        String json = new JsonLogFormatter().format(event);
        assertTrue(json.contains("\"context\":{\"a\":\"first\",\"timestamp\":\"field\",\"z\":\"\\\"\\n\\\\\"}"));
        assertFalse(json.contains("\n"));
        String plain = new PlainTextFormatter().format(event);
        assertTrue(plain.contains("context={a=\"first\",timestamp=\"field\",z=\"\\\"\\n\\\\\"}"));
        assertFalse(plain.contains("\n"));
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void badMaskerRunsBeforeAnySinkAndGuardRecovers(boolean returnsNull) {
        var once = new AtomicBoolean(true);
        var memory = new MemoryLogSink();
        var logger = new Logger("x", LogLevel.INFO, new PlainTextFormatter(), List.of(new LogTarget("m", LogLevel.INFO,
                memory)), FailurePolicy.CONTINUE, CLOCK, context -> {
            if (once.getAndSet(false)) {
                if (returnsNull)
                    return null;
                throw new IllegalArgumentException("masker");
            }
            return context;
        });
        assertThrows(RuntimeException.class, () -> logger.log(LogLevel.INFO, "x"));
        assertTrue(memory.records().isEmpty());
        assertEquals(WRITTEN, logger.log(LogLevel.INFO, "next").deliveries().getFirst().status());
    }

    @Test
    void filteringSkipsMaskerAndMaskingDoesNotInspectMessage() {
        var logger = new Logger("x", LogLevel.ERROR, new PlainTextFormatter(), List.of(new LogTarget("m",
                LogLevel.TRACE, new MemoryLogSink())), FailurePolicy.CONTINUE, CLOCK, context -> {
            throw new AssertionError("masker");
        });
        assertTrue(logger.log(LogLevel.INFO, LogContext.empty(), "x").event().isEmpty());
        var masked = new Logger("x", LogLevel.INFO, new PlainTextFormatter(), List.of(new LogTarget("m", LogLevel.TRACE,
                new MemoryLogSink())), FailurePolicy.CONTINUE, CLOCK, ContextMasker.redact(Set.of("password"), "***"));
        var result = masked.log(LogLevel.INFO, new LogContext(Map.of("password", "secret")), "secret-in-message");
        assertEquals("secret-in-message", result.event().orElseThrow().message());
    }

    @Test
    void failureKeepsNullAndEmptyMessagesDistinct() {
        for (String message : new String[] { null, "" }) {
            var logger = new Logger("x", LogLevel.INFO, new PlainTextFormatter(), List.of(new LogTarget("bad",
                    LogLevel.INFO, text -> {
                throw new IOException(message);
            })), FailurePolicy.CONTINUE, CLOCK);
            var failure = logger.log(LogLevel.INFO, "x").deliveries().getFirst().failure().orElseThrow();
            assertEquals(IOException.class.getName(), failure.exceptionType());
            assertEquals(Optional.ofNullable(message), failure.message());
        }
    }
}
