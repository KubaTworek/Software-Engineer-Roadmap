package pl.jakubtworek.backend_engineering.stage_1.block_a.testing;

/**
 * Helper class used in tests to execute the same test logic
 * multiple times in sequence.
 *
 * Repeating the same test increases the chance of detecting
 * intermittent issues that may not appear during a single run.
 */
public class RepeatHelper {

    /**
     * Executes the provided test logic a specified number of times.
     *
     * @param times number of repetitions
     * @param testLogic code representing the test scenario
     */
    public static void repeat(int times, Runnable testLogic) {

        // Run the test logic repeatedly according to the specified count.
        for (int i = 0; i < times; i++) {

            // Execute the provided test operation.
            testLogic.run();
        }
    }
}