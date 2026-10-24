package pl.jakubtworek.lld.cache.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Zachowanie LRU i TTL badamy przez wyniki oraz kolejność publicznego widoku.
 * Zegar jest kontrolowany, a granice czasu nie zależą od szybkości komputera.
 */
class LruCacheTest {

    static final Instant START = Instant.parse("2026-10-23T12:00:00Z");

    /**
     * Wygodny wspólny adapter dla dawnych scenariuszy LRU. Niezależne skoki
     * daty i upływu bada CacheExtensionsTest.Time, który rozdziela te pola.
     */
    static final class TestClock extends Clock implements Ticker {

        Instant now = START;
        int calls;
        int reads;
        boolean fail;

        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        public Clock withZone(ZoneId zone) {
            return Clock.fixed(now, zone);
        }

        public Instant instant() {
            calls++;
            if (fail)
                throw new IllegalStateException("clock");
            return now;
        }

        public long read() {
            reads++;
            if (fail)
                throw new IllegalStateException("ticker");
            return Duration.between(START, now).toNanos();
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }
    }

    static <K, V> List<K> keys(Cache<K, V> cache) {
        return cache.snapshot().leastToMostRecent().stream().map(CacheItem::key).toList();
    }

    @Test
    void fullScenarioEvictsLeastRecentlyUsedAfterHit() {
        Cache<String, Integer> cache = new InMemoryCache<>(2, () -> 0L, Clock.fixed(START, ZoneOffset.UTC));
        cache.put("a", 1);
        cache.put("b", 2);
        assertEquals(Optional.of(1), cache.get("a"));
        assertEquals(List.of("b", "a"), keys(cache));
        cache.put("c", 3);
        assertEquals(Optional.empty(), cache.get("b"));
        assertEquals(List.of("a", "c"), keys(cache));
    }

    @Test
    void peekAndMissDoNotTouchOrderButPutUpdateDoes() {
        var cache = new InMemoryCache<String, Integer>(2, () -> 0L, Clock.fixed(START, ZoneOffset.UTC));
        cache.put("a", 1);
        cache.put("b", 2);
        assertEquals(Optional.of(1), cache.peek("a"));
        assertTrue(cache.get("missing").isEmpty());
        assertEquals(List.of("a", "b"), keys(cache));
        cache.put("a", 10);
        assertEquals(List.of("b", "a"), keys(cache));
        assertEquals(2, cache.size());
        assertEquals(Optional.of(10), cache.peek("a"));
    }

    @ParameterizedTest
    @ValueSource(ints = { 0, -1, Integer.MIN_VALUE })
    void rejectsNonPositiveCapacity(int capacity) {
        assertThrows(IllegalArgumentException.class, () -> new InMemoryCache<>(capacity, () -> 0L, Clock.systemUTC()));
    }

    @Test
    void capacityOneAndEqualKeysHaveOneSlot() {
        record Key(int id) {
        }
        Cache<Key, String> cache = new InMemoryCache<>(1, () -> 0L, Clock.fixed(START, ZoneOffset.UTC));
        cache.put(new Key(1), "old");
        cache.put(new Key(1), "new");
        assertEquals(1, cache.size());
        assertEquals(Optional.of("new"), cache.get(new Key(1)));
        cache.put(new Key(2), "other");
        assertTrue(cache.get(new Key(1)).isEmpty());
    }

    @Test
    void invalidateReturnsOnlyLiveValueAndClearEmptiesEverything() {
        var clock = new TestClock();
        var cache = new InMemoryCache<String, String>(3, clock, clock);
        cache.put("a", "x");
        cache.put("b", "y", Duration.ofSeconds(1));
        assertEquals(Optional.of("x"), cache.invalidate("a"));
        assertTrue(cache.invalidate("missing").isEmpty());
        clock.advance(Duration.ofSeconds(1));
        assertTrue(cache.invalidate("b").isEmpty());
        assertEquals(0, cache.size());
        cache.put("c", "z");
        cache.clear();
        cache.clear();
        assertEquals(List.of(), keys(cache));
    }

    @ParameterizedTest
    @ValueSource(longs = { -1, 0, 1 })
    void ttlBoundaryIsInclusiveAtDeadline(long nanosFromDeadline) {
        var clock = new TestClock();
        var cache = new InMemoryCache<String, String>(2, clock, clock);
        cache.put("a", "x", Duration.ofSeconds(1));
        clock.now = START.plusSeconds(1).plusNanos(nanosFromDeadline);
        assertEquals(nanosFromDeadline < 0, cache.get("a").isPresent());
    }

    @Test
    void getTouchesLruButNeverExtendsTtl() {
        var clock = new TestClock();
        var cache = new InMemoryCache<String, String>(2, clock, clock);
        cache.put("a", "x", Duration.ofSeconds(10));
        cache.put("b", "y");
        clock.advance(Duration.ofSeconds(9));
        cache.get("a");
        assertEquals(List.of("b", "a"), keys(cache));
        assertEquals(Optional.of(Duration.ofSeconds(1)), cache.snapshot().leastToMostRecent().getLast().remainingTtl());
        clock.advance(Duration.ofSeconds(1));
        assertTrue(cache.get("a").isEmpty());
        assertEquals(Optional.of("y"), cache.get("b"));
    }

    @Test
    void expiredMruIsRemovedBeforeEvictingLiveLru() {
        var clock = new TestClock();
        var cache = new InMemoryCache<String, Integer>(2, clock, clock);
        cache.put("live", 1);
        cache.put("expires", 2, Duration.ofSeconds(1));
        clock.advance(Duration.ofSeconds(1));
        cache.put("new", 3);
        assertEquals(List.of("live", "new"), keys(cache));
        assertEquals(Optional.of(1), cache.peek("live"));
    }

    @Test
    void updateResetsDeadlineAndPermanentUpdateRemovesIt() {
        var clock = new TestClock();
        var cache = new InMemoryCache<String, String>(2, clock, clock);
        cache.put("a", "v1", Duration.ofSeconds(1));
        clock.advance(Duration.ofMillis(500));
        cache.put("a", "v2", Duration.ofSeconds(2));
        clock.advance(Duration.ofMillis(500));
        assertEquals(Optional.of("v2"), cache.peek("a"));
        cache.put("a", "v3");
        clock.advance(Duration.ofDays(1000));
        assertEquals(Optional.of("v3"), cache.peek("a"));
        assertTrue(cache.snapshot().leastToMostRecent().getFirst().remainingTtl().isEmpty());
    }

    @Test
    void cleanupSizeAndSnapshotObserveExpirationWithoutTouchingSurvivors() {
        var clock = new TestClock();
        var cache = new InMemoryCache<Integer, Integer>(3, clock, clock);
        cache.put(1, 10);
        cache.put(2, 20, Duration.ofSeconds(1));
        cache.put(3, 30);
        clock.advance(Duration.ofSeconds(1));
        assertEquals(1, cache.cleanUp());
        assertEquals(0, cache.cleanUp());
        assertEquals(2, cache.size());
        assertEquals(List.of(1, 3), keys(cache));
        cache.put(4, 40, Duration.ofSeconds(1));
        clock.advance(Duration.ofSeconds(1));
        assertEquals(2, cache.size());
        assertEquals(START.plusSeconds(2), cache.snapshot().observedAt());
    }

    @Test
    void oldSnapshotKeepsOrderAndValuesAfterChangesAndTime() {
        var clock = new TestClock();
        var cache = new InMemoryCache<String, String>(2, clock, clock);
        cache.put("a", "old", Duration.ofSeconds(1));
        cache.put("b", "b");
        var old = cache.snapshot();
        cache.get("a");
        cache.put("a", "new");
        clock.advance(Duration.ofSeconds(2));
        cache.clear();
        assertEquals(List.of("a", "b"), old.leastToMostRecent().stream().map(CacheItem::key).toList());
        assertEquals("old", old.leastToMostRecent().getFirst().value());
        assertThrows(UnsupportedOperationException.class, () -> old.leastToMostRecent().clear());
    }

    @Test
    void genericValuesAreSharedReferencesNotDeepCopies() {
        // To dowód granicy kontraktu, nie zalecenie używania mutowalnego V.
        var value = new ArrayList<>(List.of("old"));
        var cache = new InMemoryCache<Integer, List<String>>(1, () -> 0L, Clock.fixed(START, ZoneOffset.UTC));
        cache.put(1, value);
        var snapshot = cache.snapshot();
        value.add("new");
        assertEquals(List.of("old", "new"), cache.get(1).orElseThrow());
        assertSame(value, snapshot.leastToMostRecent().getFirst().value());
    }

    @Test
    void invalidInputsDoNotReadClockOrChangeLiveOrder() {
        var clock = new TestClock();
        var cache = new InMemoryCache<String, String>(2, clock, clock);
        cache.put("a", "x");
        cache.put("b", "y");
        int before = clock.reads;
        assertThrows(NullPointerException.class, () -> cache.put(null, "z"));
        assertThrows(NullPointerException.class, () -> cache.put("z", null));
        assertThrows(NullPointerException.class, () -> cache.get(null));
        assertThrows(NullPointerException.class, () -> cache.peek(null));
        assertThrows(NullPointerException.class, () -> cache.invalidate(null));
        assertThrows(NullPointerException.class, () -> cache.put("z", "v", null));
        assertThrows(IllegalArgumentException.class, () -> cache.put("z", "v", Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> cache.put("z", "v", Duration.ofNanos(-1)));
        assertEquals(before, clock.reads);
        assertEquals(List.of("a", "b"), keys(cache));
    }

    @Test
    void sourceFailureBeforeMutationPreservesOrderAndClearNeedsNoTime() {
        var clock = new TestClock();
        var cache = new InMemoryCache<String, String>(2, clock, clock);
        cache.put("a", "x");
        cache.put("b", "y");
        clock.fail = true;
        List<Runnable> operations = List.of(() -> cache.get("a"), () -> cache.peek("a"), () -> cache.put("z", "v"),
                () -> cache.put("z", "v", Duration.ofSeconds(1)), () -> cache.invalidate("a"), cache::size,
                cache::snapshot, cache::cleanUp);
        for (var operation : operations) assertThrows(IllegalStateException.class, operation::run);
        clock.fail = false;
        assertEquals(List.of("a", "b"), keys(cache));
        clock.fail = true;
        cache.clear();
        clock.fail = false;
        assertEquals(0, cache.size());
    }

    @Test
    void deadlineOverflowDoesNotEvenPurgeExpiredEntriesOrAdvanceLastTick() {
        var clock = new TestClock();
        var cache = new InMemoryCache<String, String>(2, clock, clock);
        cache.put("a", "x", Duration.ofSeconds(1));
        clock.now = START.plusNanos(Long.MAX_VALUE);
        assertThrows(ArithmeticException.class, () -> cache.put("b", "y", Duration.ofSeconds(1)));
        // Cofnięcie kontrolowanego zegara jest tu sondą: purge po błędnym put
        // usunąłby a fizycznie. Nieudana operacja nie zatwierdza nawet ostatniego ticka.
        clock.now = START;
        assertEquals(Optional.of("x"), cache.peek("a"));
    }

    @Test
    void everyTimedOperationReadsTickerOnceAndOnlySnapshotReadsClock() {
        var clock = new TestClock();
        var cache = new InMemoryCache<Integer, Integer>(2, clock, clock);
        List<Runnable> operations = List.of(() -> cache.put(1, 1), () -> cache.put(2, 2, Duration.ofSeconds(1)),
                () -> cache.get(1), () -> cache.peek(1), () -> cache.invalidate(9), cache::size, cache::cleanUp,
                cache::snapshot);
        for (var operation : operations) {
            int before = clock.reads;
            operation.run();
            assertEquals(before + 1, clock.reads);
        }
        int before = clock.reads;
        cache.clear();
        assertEquals(before, clock.reads);
        assertEquals(1, clock.calls);
    }

    @Test
    void tickerRollbackIsRejectedInsteadOfResurrectingEntry() {
        var clock = new TestClock();
        var cache = new InMemoryCache<String, String>(2, clock, clock);
        cache.put("a", "x", Duration.ofSeconds(1));
        clock.advance(Duration.ofSeconds(2));
        assertTrue(cache.get("a").isEmpty());
        var stats = cache.statistics();
        clock.now = START;
        assertThrows(IllegalStateException.class, () -> cache.peek("a"));
        assertEquals(stats, cache.statistics());
    }
}
