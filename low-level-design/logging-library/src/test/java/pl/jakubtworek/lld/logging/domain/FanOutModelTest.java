package pl.jakubtworek.lld.logging.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import pl.jakubtworek.lld.logging.sink.*;
import java.io.IOException;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;
import static pl.jakubtworek.lld.logging.domain.Delivery.Status.*;

/**
 * Model najpierw wyznacza zbiór dopuszczonych tras i indeks pierwszej awarii,
 * potem plan prób. Nie korzysta z Logger ani LogLevel.atLeast do wyroczni.
 * Bufory modeluje listami, nie ArrayDeque. Historia wielu logów obejmuje
 * progi, błędy I/O/runtime/pojemności, maskowanie i clear bez resetu liczników.
 */
class FanOutModelTest {

    @ParameterizedTest
    @EnumSource(FailurePolicy.class)
    void generatedHistoriesMatchIndependentDeliveryPlan(FailurePolicy policy) {
        var random = new Random(15002342L);
        var covered = EnumSet.noneOf(Delivery.Status.class);
        var failures = new HashSet<String>();
        var levels = List.of(LogLevel.TRACE, LogLevel.DEBUG, LogLevel.INFO, LogLevel.WARN, LogLevel.ERROR);
        for (int scenario = 0; scenario < 100; scenario++) {
            int count = 1 + random.nextInt(6);
            var actualSinks = new ArrayList<MemoryLogSink>();
            var stored = new ArrayList<List<String>>();
            int[] capacities = new int[count];
            OverflowPolicy[] overflow = new OverflowPolicy[count];
            long[] rejected = new long[count], dropped = new long[count];
            for (int i = 0; i < count; i++) {
                capacities[i] = 1 + random.nextInt(5);
                overflow[i] = random.nextBoolean() ? OverflowPolicy.REJECT_NEW : OverflowPolicy.DROP_OLDEST;
                actualSinks.add(new MemoryLogSink(capacities[i], overflow[i]));
                stored.add(new ArrayList<>());
            }
            for (int step = 0; step < 100; step++) {
                String description = policy + " scenario=" + scenario + " step=" + step;
                if (random.nextInt(12) == 0) {
                    int id = random.nextInt(count);
                    actualSinks.get(id).clear();
                    stored.get(id).clear();
                    continue;
                }
                int level = random.nextInt(5), global = random.nextInt(5);
                int[] thresholds = new int[count], modes = new int[count];
                String[] errorType = new String[count], errorMessage = new String[count];
                for (int i = 0; i < count; i++) {
                    thresholds[i] = random.nextInt(5);
                    modes[i] = random.nextInt(4);
                    if (modes[i] == 1) {
                        errorType[i] = IOException.class.getName();
                        errorMessage[i] = "io-" + i;
                    } else if (modes[i] == 2) {
                        errorType[i] = IllegalStateException.class.getName();
                        errorMessage[i] = "runtime-" + i;
                    } else if (stored.get(i).size() == capacities[i] && overflow[i] == OverflowPolicy.REJECT_NEW) {
                        errorType[i] = SinkCapacityException.class.getName();
                        errorMessage[i] = "Pełny bufor pamięciowy, capacity=" + capacities[i];
                    }
                }
                List<Integer> allowed = IntStream.range(0, count).filter(i -> level >= global && level >= thresholds[i])
                        .boxed().toList();
                int cutoff = policy == FailurePolicy.CONTINUE ? count : allowed.stream()
                        .filter(i -> errorType[i] != null).findFirst().orElse(count);
                Set<Integer> planned = new HashSet<>(allowed.stream().filter(i -> i <= cutoff).toList());
                var calls = new ArrayList<Integer>();
                var targets = new ArrayList<LogTarget>();
                for (int i = 0; i < count; i++) {
                    int id = i;
                    targets.add(new LogTarget("sink" + i, levels.get(thresholds[i]), text -> {
                        calls.add(id);
                        if (modes[id] == 1)
                            throw new IOException("io-" + id);
                        if (modes[id] == 2)
                            throw new IllegalStateException("runtime-" + id);
                        actualSinks.get(id).write(text);
                    }));
                }
                var supplierCalls = new AtomicInteger();
                var formatCalls = new AtomicInteger();
                var clockCalls = new AtomicInteger();
                Clock clock = new Clock() {

                    public ZoneId getZone() {
                        return ZoneOffset.UTC;
                    }

                    public Clock withZone(ZoneId zone) {
                        return this;
                    }

                    public Instant instant() {
                        clockCalls.incrementAndGet();
                        return Instant.EPOCH;
                    }
                };
                var logger = new Logger("model", levels.get(global), event -> {
                    formatCalls.incrementAndGet();
                    return event.message() + "|" + event.context().fields().get("requestId") + "|" + event.context()
                            .fields().get("password");
                }, targets, policy, clock, ContextMasker.redact(Set.of("password"), "***"));
                String message = "message-" + step, request = "request-" + step;
                var context = new LogContext(Map.of("requestId", request, "password", "private-" + step));
                var result = logger.log(levels.get(level), context, () -> {
                    supplierCalls.incrementAndGet();
                    return message;
                });
                assertEquals(allowed.isEmpty() ? 0 : 1, supplierCalls.get(), description);
                assertEquals(supplierCalls.get(), clockCalls.get(), description);
                assertEquals(supplierCalls.get(), formatCalls.get(), description);
                assertEquals(!allowed.isEmpty(), result.event().isPresent(), description);
                result.event().ifPresent(event -> {
                    assertEquals("***", event.context().fields().get("password"), description);
                    assertEquals(message, event.message(), description);
                });
                assertEquals(allowed.stream().filter(planned::contains).toList(), calls, description);
                assertEquals(count, result.deliveries().size(), description);
                for (int i = 0; i < count; i++) {
                    Delivery.Status expected = !allowed.contains(i) ? FILTERED : !planned.contains(i) ? NOT_ATTEMPTED : errorType[i] != null ? FAILED : WRITTEN;
                    var delivery = result.deliveries().get(i);
                    covered.add(expected);
                    assertEquals("sink" + i, delivery.target(), description);
                    assertEquals(expected, delivery.status(), description);
                    if (expected == FAILED) {
                        failures.add(errorType[i]);
                        assertEquals(errorType[i], delivery.failure().orElseThrow().exceptionType(), description);
                        assertEquals(Optional.of(errorMessage[i]), delivery.failure().orElseThrow().message(),
                                description);
                        if (errorType[i].equals(SinkCapacityException.class.getName()))
                            rejected[i]++;
                    } else
                        assertTrue(delivery.failure().isEmpty(), description);
                    if (expected == WRITTEN) {
                        if (stored.get(i).size() == capacities[i]) {
                            stored.get(i).removeFirst();
                            dropped[i]++;
                        }
                        stored.get(i).add(message + "|" + request + "|***");
                    }
                    assertEquals(stored.get(i), actualSinks.get(i).records(), description);
                    var diagnostics = actualSinks.get(i).diagnostics();
                    assertEquals(rejected[i], diagnostics.rejected(), description);
                    assertEquals(dropped[i], diagnostics.dropped(), description);
                    assertTrue(diagnostics.size() <= capacities[i], description);
                }
                assertEquals("private-" + step, context.fields().get("password"), description);
            }
        }
        assertTrue(covered.containsAll(policy == FailurePolicy.CONTINUE ? EnumSet.of(FILTERED, FAILED,
                WRITTEN) : EnumSet.allOf(Delivery.Status.class)));
        assertEquals(Set.of(IOException.class.getName(), IllegalStateException.class.getName(),
                SinkCapacityException.class.getName()), failures);
    }
}
