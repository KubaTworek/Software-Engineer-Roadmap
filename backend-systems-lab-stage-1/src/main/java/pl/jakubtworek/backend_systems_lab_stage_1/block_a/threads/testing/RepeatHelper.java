package pl.jakubtworek.backend_systems_lab_stage_1.block_a.threads.testing;

/**
 * Utility to repeat concurrency tests multiple times.
 *
 * Concurrency bugs are nondeterministic.
 * Repeating increases detection probability.
 */
public class RepeatHelper {

    public static void repeat(int times, Runnable testLogic) {
        for (int i = 0; i < times; i++) {
            testLogic.run();
        }
    }
}