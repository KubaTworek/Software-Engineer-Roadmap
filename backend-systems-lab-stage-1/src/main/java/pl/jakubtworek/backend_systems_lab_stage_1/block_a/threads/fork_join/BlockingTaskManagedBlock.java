package pl.jakubtworek.backend_systems_lab_stage_1.block_a.threads.fork_join;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

/**
 * Task that recursively creates subtasks and simulates blocking work
 * at the leaf level of the recursion tree.
 *
 * RecursiveAction is used because this task does not return a result.
 */
public class BlockingTaskManagedBlock extends RecursiveAction {

    // Current recursion level
    private final int depth;

    // Maximum depth of the task tree
    private final int maxDepth;

    // Duration of the simulated blocking operation
    private final long blockMillis;

    public BlockingTaskManagedBlock(int depth, int maxDepth, long blockMillis) {
        this.depth = depth;
        this.maxDepth = maxDepth;
        this.blockMillis = blockMillis;
    }

    @Override
    protected void compute() {

        // When the maximum depth is reached,
        // the task performs a blocking operation
        if (depth >= maxDepth) {
            managedSleep(blockMillis);
            return;
        }

        // Create two child tasks representing the next level of recursion
        BlockingTaskManagedBlock left =
                new BlockingTaskManagedBlock(depth + 1, maxDepth, blockMillis);

        BlockingTaskManagedBlock right =
                new BlockingTaskManagedBlock(depth + 1, maxDepth, blockMillis);

        // Schedule both subtasks in the ForkJoinPool and wait for completion
        invokeAll(left, right);
    }

    private void managedSleep(long ms) {
        try {

            // managedBlock informs ForkJoinPool that this worker thread
            // may block, allowing the pool to compensate if needed
            ForkJoinPool.managedBlock(new ForkJoinPool.ManagedBlocker() {

                // Indicates whether the blocking operation finished
                private boolean done = false;

                @Override
                public boolean block() {
                    if (!done) {
                        try {
                            // Simulated blocking operation
                            Thread.sleep(ms);
                        } catch (InterruptedException ignored) {}
                        done = true;
                    }
                    return true;
                }

                @Override
                public boolean isReleasable() {
                    // Allows the pool to check if blocking already finished
                    return done;
                }
            });

        } catch (InterruptedException ignored) {
        }
    }
}