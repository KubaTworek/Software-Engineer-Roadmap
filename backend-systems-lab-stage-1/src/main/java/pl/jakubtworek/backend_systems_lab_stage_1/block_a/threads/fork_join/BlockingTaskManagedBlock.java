package pl.jakubtworek.backend_systems_lab_stage_1.block_a.threads.fork_join;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

/**
 * Correct approach when you must block inside ForkJoinPool:
 *
 * ForkJoinPool.managedBlock informs the pool that a worker is blocked,
 * allowing the pool to compensate (e.g., by creating/activating spare workers),
 * reducing starvation risk.
 *
 * Still: Prefer not to block in FJP at all. Use ExecutorService for blocking work.
 */
public class BlockingTaskManagedBlock extends RecursiveAction {

    private final int depth;
    private final int maxDepth;
    private final long blockMillis;

    public BlockingTaskManagedBlock(int depth, int maxDepth, long blockMillis) {
        this.depth = depth;
        this.maxDepth = maxDepth;
        this.blockMillis = blockMillis;
    }

    @Override
    protected void compute() {
        if (depth >= maxDepth) {
            managedSleep(blockMillis);
            return;
        }

        BlockingTaskManagedBlock left = new BlockingTaskManagedBlock(depth + 1, maxDepth, blockMillis);
        BlockingTaskManagedBlock right = new BlockingTaskManagedBlock(depth + 1, maxDepth, blockMillis);

        invokeAll(left, right);
    }

    private void managedSleep(long ms) {
        try {
            ForkJoinPool.managedBlock(new ForkJoinPool.ManagedBlocker() {

                private boolean done = false;

                @Override
                public boolean block() {
                    if (!done) {
                        try {
                            Thread.sleep(ms);
                        } catch (InterruptedException ignored) {}
                        done = true;
                    }
                    return true;
                }

                @Override
                public boolean isReleasable() {
                    return done;
                }
            });
        } catch (InterruptedException ignored) {
        }
    }
}