package pl.jakubtworek.backend_systems_lab_stage_1.block_a.threads.deadlock;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class DeadlockTest {

    @Test
    void deadlockingAccounts_shouldDeadlock() throws InterruptedException {

        DeadlockingAccount acc1 = new DeadlockingAccount(100);
        DeadlockingAccount acc2 = new DeadlockingAccount(100);

        Thread t1 = new Thread(() -> acc1.transfer(acc2, 10));
        Thread t2 = new Thread(() -> acc2.transfer(acc1, 20));

        t1.start();
        t2.start();

        Thread.sleep(500);

        assertTrue(t1.isAlive() && t2.isAlive(),
                "Threads should be deadlocked");
    }

    @Test
    void orderedLockAccounts_shouldNotDeadlock() throws InterruptedException {

        OrderedLockAccount acc1 = new OrderedLockAccount(1, 100);
        OrderedLockAccount acc2 = new OrderedLockAccount(2, 100);

        Thread t1 = new Thread(() -> acc1.transfer(acc2, 10));
        Thread t2 = new Thread(() -> acc2.transfer(acc1, 20));

        t1.start();
        t2.start();

        t1.join(1000);
        t2.join(1000);

        assertFalse(t1.isAlive() || t2.isAlive(),
                "No deadlock should occur");
    }

    @Test
    void tryLockAccounts_shouldNotDeadlock() throws InterruptedException {

        TryLockAccount acc1 = new TryLockAccount(1, 100);
        TryLockAccount acc2 = new TryLockAccount(2, 100);

        Thread t1 = new Thread(() -> acc1.transfer(acc2, 10));
        Thread t2 = new Thread(() -> acc2.transfer(acc1, 20));

        t1.start();
        t2.start();

        t1.join(2000);
        t2.join(2000);

        assertFalse(t1.isAlive() || t2.isAlive(),
                "tryLock version should not deadlock");
    }

    @Test
    void trySingleThread_shouldNotDeadlock() throws InterruptedException {

        TryLockAccount acc1 = new TryLockAccount(1, 100);
        TryLockAccount acc2 = new TryLockAccount(2, 100);

        Thread t1 = new Thread(() -> acc1.transfer(acc2, 10));
        Thread t2 = new Thread(() -> acc2.transfer(acc1, 20));

        t1.start();
        t2.start();

        t1.join(2000);
        t2.join(2000);

        assertFalse(t1.isAlive() || t2.isAlive(),
                "tryLock version should not deadlock");
    }

    @Test
    void shouldNotDeadlockAndPreserveTotalBalance() throws InterruptedException {

        SingleThreadTransferService service =
                new SingleThreadTransferService();

        SingleThreadTransferService.AccountData acc1 =
                new SingleThreadTransferService.AccountData(100);

        SingleThreadTransferService.AccountData acc2 =
                new SingleThreadTransferService.AccountData(100);

        int initialTotal = acc1.getBalance() + acc2.getBalance();

        int threads = 50;
        CountDownLatch latch = new CountDownLatch(threads);
        ExecutorService executor = Executors.newFixedThreadPool(threads);

        for (int i = 0; i < threads; i++) {
            executor.execute(() -> {
                service.transfer(acc1, acc2, 1);
                service.transfer(acc2, acc1, 1);
                latch.countDown();
            });
        }

        latch.await();
        executor.shutdown();

        int finalTotal = acc1.getBalance() + acc2.getBalance();

        assertEquals(initialTotal, finalTotal,
                "Total balance should remain constant");

        assertFalse(Thread.currentThread().isInterrupted(),
                "No deadlock should occur");
    }
}