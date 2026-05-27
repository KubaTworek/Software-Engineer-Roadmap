package pl.jakubtworek.backend_engineering.stage_1.block_a.testing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DeadlockDetectionTest {

    @Test
    void shouldDetectPotentialDeadlock() throws InterruptedException {

        Object lock1 = new Object();
        Object lock2 = new Object();

        Thread t1 = new Thread(() -> {
            synchronized (lock1) {
                sleep(100);
                synchronized (lock2) {}
            }
        });

        Thread t2 = new Thread(() -> {
            synchronized (lock2) {
                sleep(100);
                synchronized (lock1) {}
            }
        });

        t1.start();
        t2.start();

        Thread.sleep(300);

        boolean deadlocked = t1.isAlive() && t2.isAlive();

        assertTrue(deadlocked,
                "Threads likely deadlocked (both still alive)");
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}