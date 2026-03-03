package pl.jakubtworek.backend_systems_lab_stage_1.block_a.fork_join;

import java.util.concurrent.RecursiveAction;

/**
 * Anti-pattern:
 * Blocking inside ForkJoinPool worker without managedBlock.
 *
 * ForkJoinPool assumes tasks are short and non-blocking.
 * If all workers block (sleep / IO / locks), the pool can starve:
 * - no free worker to run queued tasks
 * - throughput collapses
 * - progress may stall (especially with small parallelism)
 */
public class BlockingTaskNoManagedBlock extends RecursiveAction {

    private final int depth;
    private final int maxDepth;
    private final long blockMillis;

    public BlockingTaskNoManagedBlock(int depth, int maxDepth, long blockMillis) {
        this.depth = depth;
        this.maxDepth = maxDepth;
        this.blockMillis = blockMillis;
    }

    @Override
    protected void compute() {
        if (depth >= maxDepth) {
            sleep(blockMillis);
            return;
        }

        // Fork multiple subtasks; if workers block, queued tasks may starve
        BlockingTaskNoManagedBlock left = new BlockingTaskNoManagedBlock(depth + 1, maxDepth, blockMillis);
        BlockingTaskNoManagedBlock right = new BlockingTaskNoManagedBlock(depth + 1, maxDepth, blockMillis);

        invokeAll(left, right);
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {}
    }
}