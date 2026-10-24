package pl.jakubtworek.lld.cache.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Dowody nowego kontraktu: dwa niezależne źródła czasu, wersje wpisów w indeksie,
 * rozdzielone przyczyny usunięć i polityka ofiary. Bez Thread.sleep.
 */
class CacheExtensionsTest {

    static final Instant START = Instant.parse("2026-10-23T12:00:00Z");

    static class Time extends Clock implements Ticker {

        Instant wall = START;
        long nanos;
        int ticks, clocks;
        boolean failTicker, failClock;

        public long read() {
            ticks++;
            if (failTicker)
                throw new IllegalStateException("ticker");
            return nanos;
        }

        public Instant instant() {
            clocks++;
            if (failClock)
                throw new IllegalStateException("clock");
            return wall;
        }

        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        public Clock withZone(ZoneId zone) {
            return Clock.fixed(wall, zone);
        }

        void advance(long seconds) {
            nanos = Math.addExact(nanos, Math.multiplyExact(seconds, 1_000_000_000L));
        }
    }

    static <K, V> List<K> keys(Cache<K, V> cache) {
        return cache.snapshot().leastToMostRecent().stream().map(CacheItem::key).toList();
    }

    @Test
    void wallClockJumpsDoNotChangeTtl() {
        var time = new Time();
        var cache = new InMemoryCache<String, String>(2, time, time);
        cache.put("a", "x", Duration.ofSeconds(2));
        time.wall = Instant.MAX;
        assertEquals(Optional.of("x"), cache.get("a"));
        assertEquals(Instant.MAX, cache.snapshot().observedAt());
        time.wall = Instant.MIN;
        time.advance(2);
        assertTrue(cache.get("a").isEmpty());
        assertEquals(Instant.MIN, cache.snapshot().observedAt());
        assertEquals(1, cache.statistics().expirations());
    }

    @Test
    void onlySnapshotReadsClockAndFailuresDoNotChangeAnything() {
        var time = new Time();
        var cache = new InMemoryCache<Integer, Integer>(2, time, time);
        cache.put(1, 1, Duration.ofSeconds(1));
        time.advance(2);
        var beforeStats = cache.statistics();
        var beforeDiagnostic = cache.diagnostics();
        time.failClock = true;
        assertThrows(IllegalStateException.class, cache::snapshot);
        assertEquals(beforeStats, cache.statistics());
        assertEquals(beforeDiagnostic, cache.diagnostics());
        // Ticker działa mimo niedostępnego Clock.
        assertEquals(1, cache.cleanUp());
        cache.put(2, 2);
        cache.get(2);
        cache.peek(2);
        cache.resize(1);
        cache.invalidate(2);
        cache.size();
        cache.clear();
        assertEquals(1, time.clocks);
    }

    @Test
    void tickerFailureRollbackAndOverflowHaveNoStateOrStatsEffects() {
        var time = new Time();
        var cache = new InMemoryCache<String, String>(2, time, time);
        cache.put("a", "x", Duration.ofSeconds(1));
        time.nanos = 2_000_000_000L;
        var stats = cache.statistics();
        var diagnostics = cache.diagnostics();
        time.failTicker = true;
        List<Runnable> operations = List.of(() -> cache.get("a"), () -> cache.peek("a"), () -> cache.put("b", "x"),
                () -> cache.invalidate("a"), cache::cleanUp, cache::snapshot, cache::size, () -> cache.resize(1));
        for (var op : operations) assertThrows(IllegalStateException.class, op::run);
        assertEquals(stats, cache.statistics());
        assertEquals(diagnostics, cache.diagnostics());
        time.failTicker = false;
        time.nanos = Long.MAX_VALUE;
        assertThrows(ArithmeticException.class, () -> cache.put("b", "x", Duration.ofNanos(1)));
        assertThrows(ArithmeticException.class, () -> cache.put("b", "x", Duration.ofSeconds(Long.MAX_VALUE)));
        assertEquals(stats, cache.statistics());
        assertEquals(diagnostics, cache.diagnostics());
        // Nieudany put nie zatwierdził lastTick.
        time.nanos = 0;
        assertEquals(Optional.of("x"), cache.peek("a"));
        time.nanos = 1;
        cache.size();
        time.nanos = 0;
        assertThrows(IllegalStateException.class, cache::size);
    }

    @Test
    void staleTicketsCannotExpireUpdatedOrRecreatedKey() {
        var time = new Time();
        var cache = new InMemoryCache<String, String>(2, time, time);
        cache.put("a", "old", Duration.ofSeconds(1));
        cache.put("a", "new", Duration.ofSeconds(4));
        time.advance(1);
        assertEquals(0, cache.cleanUp());
        assertEquals(Optional.of("new"), cache.get("a"));
        cache.invalidate("a");
        cache.put("a", "permanent");
        time.advance(3);
        assertEquals(0, cache.cleanUp());
        assertEquals(Optional.of("permanent"), cache.peek("a"));
        assertEquals(0, cache.statistics().expirations());
    }

    @Test
    void sameDeadlineDifferentVersionsStillExpireOnce() {
        var time = new Time();
        var cache = new InMemoryCache<String, Integer>(1, time, time);
        for (int i = 0; i < 10; i++) cache.put("a", i, Duration.ofSeconds(1));
        time.advance(1);
        assertEquals(1, cache.cleanUp());
        assertEquals(1, cache.statistics().expirations());
        assertEquals(0, cache.diagnostics().scheduledRecords());
    }

    @Test
    void indexIsBoundedUnderThousandsOfUpdatesAndShrinksAfterRemoval() {
        var time = new Time();
        var cache = new InMemoryCache<String, Integer>(2, time, time);
        for (int i = 0; i < 3000; i++) {
            cache.put("a", i, Duration.ofDays(10));
            var d = cache.diagnostics();
            assertEquals(d.timedEntries(), d.scheduledRecords());
        }
        // Indeks usuwa stare TTL bez przebudowy całego kopca.
        assertEquals(0, cache.diagnostics().compactions());
        cache.invalidate("a");
        assertEquals(0, cache.diagnostics().scheduledRecords());
        cache.put("b", 2, Duration.ofDays(1));
        cache.clear();
        assertEquals(0, cache.diagnostics().scheduledRecords());
    }

    @ParameterizedTest
    @EnumSource(EvictionPolicy.class)
    void policiesDifferAndPeekNeverRaisesFrequency(EvictionPolicy policy) {
        var time = new Time();
        var cache = new InMemoryCache<String, Integer>(2, policy, time, time);
        cache.put("a", 1);
        cache.get("a");
        cache.get("a");
        cache.put("b", 2);
        for (int i = 0; i < 10; i++) cache.peek("b");
        cache.put("c", 3);
        assertEquals(policy == EvictionPolicy.LRU ? List.of("b", "c") : List.of("a", "c"), keys(cache));
        // Peek to odczyt diagnostyczny.
        assertEquals(2, cache.statistics().hits());
        assertEquals(1, cache.statistics().evictions());
    }

    @Test
    void lfuTieUsesLruAndUpdateCountsAsUseButReplacementAfterExpiryResetsFrequency() {
        var time = new Time();
        var cache = new InMemoryCache<String, Integer>(2, EvictionPolicy.LFU, time, time);
        cache.put("a", 1);
        cache.put("b", 2);
        cache.put("c", 3);
        assertEquals(List.of("b", "c"), keys(cache));
        cache.put("b", 20);
        assertEquals(2, cache.snapshot().leastToMostRecent().getLast().frequency());
        cache.put("c", 30, Duration.ofSeconds(1));
        time.advance(1);
        cache.put("c", 40);
        assertEquals(1, cache.snapshot().leastToMostRecent().getLast().frequency());
    }

    @Test
    void countersDistinguishReasonsAndClearDoesNotResetThem() {
        var time = new Time();
        var cache = new InMemoryCache<String, Integer>(2, time, time);
        cache.put("a", 1, Duration.ofSeconds(1));
        cache.put("b", 2);
        cache.get("b");
        cache.get("missing");
        cache.peek("a");
        time.advance(1);
        cache.peek("a");
        cache.put("c", 3);
        // Dopiero drugi zapis przekracza pojemność i wypiera b.
        cache.put("d", 4);
        cache.invalidate("c");
        cache.invalidate("missing");
        cache.clear();
        assertEquals(new CacheStatistics(1, 1, 1, 1, 1, 1), cache.statistics());
        cache.put("x", 1, Duration.ofSeconds(1));
        time.advance(1);
        // clear nie uruchamia purge: zaległy wpis liczy jako jawne usunięcie.
        cache.clear();
        assertEquals(2, cache.statistics().clearRemovals());
        assertEquals(1, cache.statistics().expirations());
        cache.resetStatistics();
        assertEquals(new CacheStatistics(0, 0, 0, 0, 0, 0), cache.statistics());
    }

    @Test
    void diagnosticsAndStatsDoNotPurgeOrReadTimeAndInvalidCommandsDoNotChangeThem() {
        var time = new Time();
        var cache = new InMemoryCache<String, Integer>(2, time, time);
        cache.put("a", 1, Duration.ofSeconds(1));
        time.advance(2);
        int reads = time.ticks;
        var old = cache.diagnostics();
        var stats = cache.statistics();
        assertEquals(1, old.physicalSize());
        assertThrows(IllegalArgumentException.class, () -> cache.resize(0));
        assertThrows(IllegalArgumentException.class, () -> cache.put("a", 2, Duration.ZERO));
        assertEquals(old, cache.diagnostics());
        assertEquals(stats, cache.statistics());
        assertEquals(reads, time.ticks);
        assertEquals(0, time.clocks);
        assertEquals(0, cache.size());
        assertEquals(1, cache.statistics().expirations());
    }

    @ParameterizedTest
    @EnumSource(EvictionPolicy.class)
    void resizeExpiresFirstThenReportsVictimsInPolicyOrderAndGrowthPreservesOrder(EvictionPolicy policy) {
        var time = new Time();
        var cache = new InMemoryCache<String, Integer>(4, policy, time, time);
        cache.put("a", 1);
        cache.get("a");
        cache.get("a");
        cache.put("b", 2);
        cache.put("c", 3);
        cache.put("expired", 4, Duration.ofSeconds(1));
        time.advance(1);
        var result = cache.resize(1);
        assertEquals(List.of(new RemovedEntry<>("expired", 4)), result.expired());
        assertEquals(policy == EvictionPolicy.LRU ? List.of(new RemovedEntry<>("a", 1), new RemovedEntry<>("b",
                2)) : List.of(new RemovedEntry<>("b", 2), new RemovedEntry<>("c", 3)), result.evicted());
        assertEquals(4, result.previousCapacity());
        assertEquals(1, result.capacity());
        assertEquals(1, cache.statistics().expirations());
        assertEquals(2, cache.statistics().evictions());
        var order = keys(cache);
        assertTrue(cache.resize(5).evicted().isEmpty());
        assertEquals(order, keys(cache));
        assertThrows(UnsupportedOperationException.class, () -> result.evicted().clear());
    }

    @Test
    void snapshotIsHistoricalAndShowsRemainingTtlNotWallClockDeadline() {
        var time = new Time();
        var cache = new InMemoryCache<Integer, List<String>>(1, time, time);
        var value = new ArrayList<>(List.of("x"));
        cache.put(1, value, Duration.ofSeconds(3));
        time.advance(1);
        var snapshot = cache.snapshot();
        assertEquals(Optional.of(Duration.ofSeconds(2)), snapshot.leastToMostRecent().getFirst().remainingTtl());
        time.advance(3);
        cache.clear();
        assertEquals(1, snapshot.leastToMostRecent().size());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.leastToMostRecent().clear());
        assertSame(value, snapshot.leastToMostRecent().getFirst().value());
    }
}
