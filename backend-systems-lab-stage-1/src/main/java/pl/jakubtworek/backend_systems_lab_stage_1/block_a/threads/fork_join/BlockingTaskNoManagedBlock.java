package pl.jakubtworek.backend_systems_lab_stage_1.block_a.threads.fork_join;

import java.util.concurrent.RecursiveAction;

/**
 * Recursive task that intentionally performs a blocking operation
 * without using ForkJoinPool.managedBlock.
 *
 * This example is meant to demonstrate what happens when ForkJoinPool
 * workers perform blocking work directly.
 */
public class BlockingTaskNoManagedBlock extends RecursiveAction {

    // Current recursion level in the task tree
    private final int depth;

    // Maximum depth that controls how many tasks will be created
    private final int maxDepth;

    // Duration of the simulated blocking operation
    private final long blockMillis;

    public BlockingTaskNoManagedBlock(int depth, int maxDepth, long blockMillis) {
        this.depth = depth;
        this.maxDepth = maxDepth;
        this.blockMillis = blockMillis;
    }

    @Override
    protected void compute() {

        // Leaf tasks simulate blocking work
        if (depth >= maxDepth) {
            sleep(blockMillis);
            return;
        }

        // Create two child tasks for the next recursion level
        BlockingTaskNoManagedBlock left =
                new BlockingTaskNoManagedBlock(depth + 1, maxDepth, blockMillis);

        BlockingTaskNoManagedBlock right =
                new BlockingTaskNoManagedBlock(depth + 1, maxDepth, blockMillis);

        // Fork both tasks and wait for their completion
        // If worker threads block later (during sleep),
        // queued tasks may not get CPU time immediately
        invokeAll(left, right);
    }

    private void sleep(long ms) {
        try {
            // Simulated blocking operation executed directly
            // inside a ForkJoinPool worker thread
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }
}