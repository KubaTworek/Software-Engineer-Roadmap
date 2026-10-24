package pl.jakubtworek.lld.cache.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static pl.jakubtworek.lld.cache.domain.CacheExtensionsTest.*;

/**
 * Wyrocznia używa HashMap i numeru ostatniego użycia, nie LinkedHashMap/kopca.
 * Fizycznie przechowuje również niesprzątane wygasłe. Po każdym kroku badamy
 * tylko czyste stats/diagnostics; snapshot jest osobną generowaną operacją.
 * Dzięki temu obserwacja nie sprząta historii między advance a clear/resize.
 */
class CacheModelTest {

    record Cell(int value, Long deadline, long lastUse, long frequency) {
    }

    @ParameterizedTest
    @EnumSource(EvictionPolicy.class)
    void mixedHistoriesAgreeWithoutImplicitSnapshotCleanup(EvictionPolicy policy) {
        var random = new Random(14002342);
        var coverage = new HashSet<Integer>();
        for (int scenario = 0; scenario < 100; scenario++) {
            var time = new Time();
            int capacity = 1 + random.nextInt(5);
            var cache = new InMemoryCache<Integer, Integer>(capacity, policy, time, time);
            var model = new Model(capacity, policy);
            for (int step = 0; step < 200; step++) {
                int op = random.nextInt(16);
                coverage.add(op);
                int key = random.nextInt(8), value = random.nextInt(100), nextCapacity = 1 + random.nextInt(5);
                long ttl = (1 + random.nextInt(5)) * 1_000_000_000L;
                String context = policy + " scenario=" + scenario + " step=" + step + " op=" + op;
                List<RemovedEntry<Integer, Integer>> expired = List.of();
                if (Set.of(0, 1, 2, 3, 4, 5, 8, 9, 11).contains(op))
                    expired = model.purge(time.nanos);
                Cell previous = model.entries.get(key);
                switch(op) {
                    case 0 ->
                        {
                            assertEquals(previous == null ? Optional.empty() : Optional.of(previous.value()),
                                    cache.get(key), context);
                            if (previous == null)
                                model.stats[1]++;
                            else {
                                model.stats[0]++;
                                model.entries.put(key, new Cell(previous.value(), previous.deadline(), ++model.sequence,
                                        previous.frequency() + 1));
                            }
                        }
                    case 1 ->
                        assertEquals(previous == null ? Optional.empty() : Optional.of(previous.value()),
                                cache.peek(key), context);
                    case 2, 3 ->
                        {
                            if (op == 2)
                                cache.put(key, value);
                            else
                                cache.put(key, value, Duration.ofNanos(ttl));
                            model.entries.put(key, new Cell(value, op == 2 ? null : time.nanos + ttl, ++model.sequence,
                                    previous == null ? 1 : previous.frequency() + 1));
                            model.evictTo(model.capacity);
                        }
                    case 4 ->
                        {
                            assertEquals(previous == null ? Optional.empty() : Optional.of(previous.value()),
                                    cache.invalidate(key), context);
                            if (model.entries.remove(key) != null)
                                model.stats[4]++;
                        }
                    case 5 ->
                        assertEquals(expired.size(), cache.cleanUp(), context);
                    case 6 ->
                        time.advance(random.nextInt(5));
                    case 7 ->
                        {
                            cache.clear();
                            model.stats[5] += model.entries.size();
                            model.entries.clear();
                        }
                    case 8 ->
                        assertEquals(model.entries.size(), cache.size(), context);
                    case 9 ->
                        compareSnapshot(model, cache.snapshot(), time, context);
                    case 10 ->
                        assertThrows(IllegalArgumentException.class, () -> cache.put(key, value, Duration.ZERO),
                                context);
                    case 11 ->
                        {
                            int old = model.capacity;
                            var victims = model.evictTo(nextCapacity);
                            model.capacity = nextCapacity;
                            var actual = cache.resize(nextCapacity);
                            assertEquals(old, actual.previousCapacity(), context);
                            assertEquals(nextCapacity, actual.capacity(), context);
                            // Deadline remisy bez kolejności.
                            assertEquals(new HashSet<>(expired), new HashSet<>(actual.expired()), context);
                            assertEquals(victims, actual.evicted(), context);
                        }
                    case 12 ->
                        assertThrows(IllegalArgumentException.class, () -> cache.resize(0), context);
                    case 13 ->
                        time.wall = time.wall.plusSeconds(random.nextInt(21) - 10);
                    case 14 ->
                        {
                            cache.resetStatistics();
                            Arrays.fill(model.stats, 0);
                        }
                    case 15 ->
                        {
                            cache.statistics();
                            cache.diagnostics();
                        }
                    default ->
                        fail(context);
                }
                assertEquals(model.statistics(), cache.statistics(), context);
                var d = cache.diagnostics();
                assertEquals(model.capacity, d.capacity(), context);
                assertEquals(policy, d.policy(), context);
                assertEquals(model.entries.size(), d.physicalSize(), context);
                assertEquals(model.entries.values().stream().filter(c -> c.deadline() != null).count(),
                        d.timedEntries(), context);
                assertEquals(d.timedEntries(), d.scheduledRecords(), context);
                if (d.timedEntries() == 0)
                    assertEquals(0, d.scheduledRecords(), context);
            }
            model.purge(time.nanos);
            compareSnapshot(model, cache.snapshot(), time, "final " + scenario);
        }
        assertEquals(16, coverage.size());
    }

    private static void compareSnapshot(Model model, CacheSnapshot<Integer, Integer> actual, Time time,
            String context) {
        var expected = model.entries.entrySet().stream().sorted(Comparator.comparingLong(e -> e.getValue().lastUse()))
                .map(e -> new CacheItem<>(e.getKey(), e.getValue().value(), e.getValue().frequency(), e.getValue()
                .deadline() == null ? Optional.<Duration>empty() : Optional.of(Duration.ofNanos(e.getValue()
                .deadline() - time.nanos)))).toList();
        assertEquals(expected, actual.leastToMostRecent(), context);
        assertEquals(model.statistics(), actual.statistics(), context);
        assertEquals(time.wall, actual.observedAt(), context);
        assertEquals(time.nanos, actual.elapsedNanos(), context);
        assertEquals(model.capacity, actual.capacity(), context);
        assertEquals(model.policy, actual.policy(), context);
    }

    private static final class Model {

        final Map<Integer, Cell> entries = new HashMap<>();
        final long[] stats = new long[6];
        final EvictionPolicy policy;
        int capacity;
        long sequence;

        Model(int capacity, EvictionPolicy policy) {
            this.capacity = capacity;
            this.policy = policy;
        }

        List<RemovedEntry<Integer, Integer>> purge(long now) {
            var expired = new ArrayList<RemovedEntry<Integer, Integer>>();
            var iterator = entries.entrySet().iterator();
            while (iterator.hasNext()) {
                var e = iterator.next();
                if (e.getValue().deadline() != null && e.getValue().deadline() <= now) {
                    expired.add(new RemovedEntry<>(e.getKey(), e.getValue().value()));
                    iterator.remove();
                    stats[2]++;
                }
            }
            return expired;
        }

        List<RemovedEntry<Integer, Integer>> evictTo(int target) {
            var result = new ArrayList<RemovedEntry<Integer, Integer>>();
            Comparator<Map.Entry<Integer, Cell>> order = Comparator.comparingLong(e -> e.getValue().lastUse());
            if (policy == EvictionPolicy.LFU)
                order = Comparator.<Map.Entry<Integer, Cell>>comparingLong(e -> e.getValue().frequency())
                        .thenComparing(order);
            while (entries.size() > target) {
                var victim = entries.entrySet().stream().min(order).orElseThrow();
                result.add(new RemovedEntry<>(victim.getKey(), victim.getValue().value()));
                entries.remove(victim.getKey());
                stats[3]++;
            }
            return result;
        }

        CacheStatistics statistics() {
            return new CacheStatistics(stats[0], stats[1], stats[2], stats[3], stats[4], stats[5]);
        }
    }
}
