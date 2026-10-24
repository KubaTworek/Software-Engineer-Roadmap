package pl.jakubtworek.marketplace.catalog;

import org.junit.jupiter.api.Test;
import pl.jakubtworek.marketplace.catalog.infrastructure.ShadowCatalogReader;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ShadowCatalogReaderTest {
    @Test @org.junit.jupiter.api.Timeout(5)
    void hungCandidateCannotHoldMonitorOrGrowQueueAndRollbackDiscardsEvidence() throws Exception {
        var blocked = new java.util.concurrent.CountDownLatch(1);
        var entered = new java.util.concurrent.CountDownLatch(1);
        try (var reader = new ShadowCatalogReader<String,String>(id -> "old", id -> {
            entered.countDown();
            // Kontrprzykład adaptera ignorującego interrupt: slot pozostaje zajęty.
            while (blocked.getCount() > 0) try { blocked.await(); } catch (InterruptedException ignored) { }
            return "old";
        }, (id, equal) -> {}, java.time.Duration.ofMillis(30), 1, java.time.Duration.ofMinutes(1), java.time.Clock.systemUTC())) {
            assertTimeout(java.time.Duration.ofSeconds(1), () -> assertEquals("old", reader.read("a")));
            assertTrue(entered.await(1, java.util.concurrent.TimeUnit.SECONDS));
            assertTimeout(java.time.Duration.ofSeconds(1), () -> assertEquals("old", reader.read("b")));
            assertThrows(IllegalStateException.class, () -> reader.promote(0, 1));
        } finally { blocked.countDown(); }
        try (var reader = new ShadowCatalogReader<String,String>(id -> "old", id -> "old", (id, equal) -> {})) {
            reader.read("a"); reader.promote(0, 1); reader.rollbackReads();
            assertThrows(IllegalStateException.class, () -> reader.promote(0, 1));
        }
    }
    @Test void shadowFailurePreservesLegacyResponseAndPromotionRequiresLagZero() throws Exception {
        List<Boolean> observations = new java.util.concurrent.CopyOnWriteArrayList<>();
        var observed = new java.util.concurrent.CountDownLatch(1);
        var reader = new ShadowCatalogReader<String,String>(id -> "legacy-price", id -> {
            throw new IllegalStateException("read model unavailable");
        }, (id, equal) -> { observations.add(equal); observed.countDown(); });
        assertEquals("legacy-price", reader.read("sku"));
        assertTrue(observed.await(1, java.util.concurrent.TimeUnit.SECONDS));
        assertEquals(List.of(false), observations);
        assertThrows(IllegalStateException.class, () -> reader.promote(1, 1));
        assertThrows(IllegalStateException.class, () -> reader.promote(0, 1));
    }
    @Test void readRollbackDoesNotPretendToMigrateWrites() {
        var candidate = new java.util.concurrent.atomic.AtomicReference<>("old");
        var reader = new ShadowCatalogReader<String,String>(id -> "old", id -> candidate.get(), (id, equal) -> {});
        assertThrows(IllegalStateException.class, () -> reader.promote(0, 1));
        reader.read("sku");
        reader.promote(0, 1);
        candidate.set("new");
        assertEquals("new", reader.read("sku"));
        reader.rollbackReads();
        assertEquals("old", reader.read("sku"));
    }
}
