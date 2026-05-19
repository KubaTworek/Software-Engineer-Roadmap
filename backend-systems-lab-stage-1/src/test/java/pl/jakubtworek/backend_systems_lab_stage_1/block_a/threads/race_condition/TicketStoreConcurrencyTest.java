package pl.jakubtworek.backend_systems_lab_stage_1.block_a.threads.race_condition;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class TicketStoreConcurrencyTest {

    private static final int THREADS = 100;

    // ===============================
    // 🔴 BROKEN — powinien łamać invariant
    // ===============================

    @Test
    void brokenStore_shouldEventuallyOversell() throws InterruptedException {

        boolean oversellingDetected = false;

        for (int i = 0; i < 10_000; i++) {
            TicketStore store = new BrokenTicketStore();
            runConcurrent(store, 2);

            if (store.getSold() > store.getInitial()) {
                oversellingDetected = true;
                break;
            }
        }

        assertTrue(oversellingDetected,
                "Race condition was not observed in BrokenTicketStore");
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
    void correctStores_shouldPreserveInvariant(TicketStore store)
            throws InterruptedException {

        runConcurrent(store, THREADS);

        assertInvariant(store);
    }

    // ===============================
    // 🔎 TEST STRESOWY
    // ===============================

    @Test
    void synchronizedStore_shouldNeverOversell_underHeavyLoad()
            throws InterruptedException {

        TicketStore store = new SynchronizedTicketStore();

        for (int i = 0; i < 1000; i++) {
            runConcurrent(store, THREADS);
            assertInvariant(store);
        }
    }

    // ===============================
    // 🧪 WSPÓLNA LOGIKA TESTOWA
    // ===============================

    private void runConcurrent(TicketStore store, int threads)
            throws InterruptedException {

        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        ExecutorService executor = Executors.newFixedThreadPool(threads);

        for (int i = 0; i < threads; i++) {
            executor.execute(() -> {
                try {
                    ready.countDown();
                    start.await();
                    store.buy();
                } catch (InterruptedException ignored) {
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();      // wszystkie wątki gotowe
        start.countDown();  // jednoczesny start
        done.await();       // czekamy na zakończenie

        executor.shutdown();
    }

    private void assertInvariant(TicketStore store) {

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
}