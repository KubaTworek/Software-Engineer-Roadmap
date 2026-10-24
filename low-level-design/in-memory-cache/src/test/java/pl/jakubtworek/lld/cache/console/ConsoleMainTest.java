package pl.jakubtworek.lld.cache.console;

import org.junit.jupiter.api.Test;
import java.io.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Parser i wynik sesji są sprawdzane bez podmiany globalnych strumieni i bez snu.
 */
class ConsoleMainTest {

    @Test
    void hitChangesEvictionAndPeekDoesNot() {
        String output = run("new 2\nput a 1\nput b 2\npeek a\nshow\nget a\nput c 3\nget b\nshow\nq\n");
        assertTrue(output.contains("LRU -> MRU: [a, b]"));
        assertTrue(output.contains("LRU -> MRU: [a, c]"));
        assertTrue(output.contains("MISS"));
    }

    @Test
    void ttlExpiresAtExactSecondAndCleanupReportsCount() {
        String output = run("put-ttl a 2 tekst ze spacjami\nadvance 1\nget a\nadvance 1\nclean\nget a\nsize\nq\n");
        assertTrue(output.contains("HIT tekst ze spacjami"));
        assertTrue(output.contains("Wygasłe usunięte: 1"));
        assertTrue(output.contains("MISS"));
        assertTrue(output.contains("Rozmiar: 0"));
    }

    @Test
    void invalidNewTtlOrAdvanceDoesNotDestroyCurrentCache() {
        String output = run("put a old\nnew 0\nput-ttl a 0 new\nadvance -1\nget a\ntime\nq\n");
        assertTrue(output.contains("Odmowa: Pojemność musi być dodatnia"));
        assertTrue(output.contains("Odmowa: TTL musi być dodatni"));
        assertTrue(output.contains("Odmowa: Nie cofamy czasu w demo"));
        assertTrue(output.contains("HIT old"));
        assertTrue(output.contains("2026-10-23T12:00:00Z"));
    }

    @Test
    void malformedCommandsDoNotStopSessionAndEofIsNormal() {
        String output = run("help\nunknown\nput a\nput-ttl a x v\nget a b\nshow extra\nput a x\nremove a\nclear\nsize\n");
        assertTrue(output.contains("Odmowa: Nieznana komenda"));
        assertTrue(output.contains("Odmowa: put KEY VALUE"));
        assertTrue(output.contains("Odmowa: Wymagany jeden argument"));
        assertTrue(output.contains("HIT x"));
        assertTrue(output.contains("Rozmiar: 0"));
    }

    @Test
    void permanentReplacementRemovesTtlAndClearWorks() {
        String output = run("put-ttl a 1 old\nput a permanent\nadvance 5\nget a\nclear\nget a\nq\n");
        assertTrue(output.contains("HIT permanent"));
        assertTrue(output.contains("MISS"));
    }

    @Test
    void lfuResizeStatisticsAndWallClockAreObservable() {
        String output = run("policy LFU\nnew 3\nput a A\nget a\nget a\nput b B\nput-ttl t 1 T\n" + "wall -100\nadvance 1\ndiagnostics\nstats\nresize 1\nshow\nget a\n" + "resize 0\nstats\nreset-stats\nstats\nq\n");
        assertTrue(output.contains("Polityka kolejnego cache: LFU"));
        assertTrue(output.contains("physicalSize=3"));
        assertTrue(output.contains("expired=[RemovedEntry[key=t, value=T]]"));
        assertTrue(output.contains("evicted=[RemovedEntry[key=b, value=B]]"));
        assertTrue(output.contains("LRU -> MRU: [a]"));
        assertTrue(output.contains("expirations=1, evictions=1"));
        assertTrue(output.contains("CacheStatistics[hits=0, misses=0, expirations=0, evictions=0"));
    }

    @Test
    void invalidPolicyAndOverflowAdvancePreserveConfigurationAndTick() {
        String output = run("policy LFU\npolicy BAD\nnew 2\nput-ttl a 1 A\n" + "advance 9223372036854775807\npeek a\nshow\nq\n");
        assertTrue(output.contains("Odmowa:"));
        assertTrue(output.contains("HIT A"));
        assertTrue(output.contains("LRU -> MRU: [a]"));
    }

    private static String run(String commands) {
        var out = new StringWriter();
        ConsoleMain.run(new StringReader(commands), new PrintWriter(out));
        return out.toString();
    }
}
