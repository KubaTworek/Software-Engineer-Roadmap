package pl.jakubtworek.backend_engineering.stage_1.block_a.race_condition;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import pl.jakubtworek.backend_engineering.stage_1.block_a.testing.ConcurrentTestHelper;

import java.util.List;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

import static org.junit.jupiter.api.Assertions.*;

class TicketStoreConcurrencyTest {

    private static final int THREADS = 100;

    // ===============================
    // 🔴 BROKEN — powinien łamać invariant
    // ===============================

    @Test
    void brokenStoreShouldOversellUnderForcedCheckThenActInterleaving()
            throws InterruptedException {
        CyclicBarrier bothBuyersReadAvailability = new CyclicBarrier(2);
        TicketStore store = new BrokenTicketStore(
                () -> await(bothBuyersReadAvailability));

        runConcurrent(store, 2);

        assertEquals(2, store.getSold());
        assertTrue(store.getSold() > store.getInitial(),
                "Both buyers passed the check for the same ticket");
    }

    // ===============================
    // 🟢 POPRAWNE IMPLEMENTACJE
    // ===============================

    static List<TicketStore> correctStores() {
        return List.of(
                new SynchronizedTicketStore(),
                new AtomicTicketStore(),
                new LockTicketStore(),
                new SingleThreadTicketStore()
        );
    }

    @ParameterizedTest
    @MethodSource("correctStores")
    void correctStoresShouldPreserveInvariant(TicketStore store)
            throws InterruptedException {
        try {
            runConcurrent(store, THREADS);
            assertInvariant(store);
        } finally {
            closeIfNecessary(store);
        }
    }

    // ===============================
    // 🔎 TEST STRESOWY
    // ===============================

    @Test
    void synchronizedStoreShouldPreserveInvariantUnderContention()
            throws InterruptedException {

        TicketStore store = new SynchronizedTicketStore();
        runConcurrent(store, THREADS);
        assertInvariant(store);
    }

    // ===============================
    // 🧪 WSPÓLNA LOGIKA TESTOWA
    // ===============================

    private static void runConcurrent(TicketStore store, int threads)
            throws InterruptedException {
        ConcurrentTestHelper.runConcurrent(threads, store::buy);
    }

    private static void assertInvariant(TicketStore store) {

        assertTrue(store.getSold() <= store.getInitial(),
                "Sold exceeded initial stock in " + store.name());

        assertTrue(store.getAvailable() >= 0,
                "Available below zero in " + store.name());

        assertEquals(
                store.getInitial(),
                store.getAvailable() + store.getSold(),
                "Invariant broken in " + store.name()
        );
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Buyer interrupted at test barrier", exception);
        } catch (BrokenBarrierException exception) {
            throw new IllegalStateException("Buyer test barrier was broken", exception);
        }
    }

    private static void closeIfNecessary(TicketStore store) {
        if (store instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception exception) {
                throw new AssertionError("Could not close ticket store", exception);
            }
        }
    }
}
