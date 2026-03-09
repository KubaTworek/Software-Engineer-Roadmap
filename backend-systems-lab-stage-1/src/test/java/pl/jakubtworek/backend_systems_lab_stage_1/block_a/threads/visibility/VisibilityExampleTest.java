package pl.jakubtworek.backend_systems_lab_stage_1.block_a.threads.visibility;

import org.junit.jupiter.api.Test;

import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class VisibilityExampleTest {

    @Test
    void volatileVersion_shouldTerminate() throws InterruptedException {

        VisibilityExampleVolatile example = new VisibilityExampleVolatile();

        Thread worker = new Thread(example::work);
        worker.start();

        Thread.sleep(100);
        example.stop();

        worker.join(1000);

        assertFalse(worker.isAlive(),
                "Volatile version should terminate");
    }

    @Test
    void synchronizedVersion_shouldTerminate() throws InterruptedException {

        VisibilityExampleSynchronized example =
                new VisibilityExampleSynchronized();

        Thread worker = new Thread(example::work);
        worker.start();

        Thread.sleep(100);
        example.stop();

        worker.join(1000);

        assertFalse(worker.isAlive(),
                "Synchronized version should terminate");
    }

    @Test
    void nonVolatileVersion_mayNeverTerminate() throws InterruptedException {

        VisibilityExample example = new VisibilityExample();

        Thread worker = new Thread(example::work);
        worker.start();

        Thread.sleep(100);
        example.stop();

        worker.join(500);

        // This test is intentionally probabilistic.
        // It may pass on some JVMs and fail on others.
        if (worker.isAlive()) {
            worker.interrupt(); // cleanup
        }

        // No hard assert here – demonstration test.
        System.out.println("Worker alive: " + worker.isAlive());
    }
}