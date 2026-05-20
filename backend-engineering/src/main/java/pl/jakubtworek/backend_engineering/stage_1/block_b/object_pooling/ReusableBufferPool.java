package pl.jakubtworek.backend_engineering.stage_1.block_b.object_pooling;

import java.util.ArrayDeque;

public final class ReusableBufferPool {

    private final ArrayDeque<ReusableBuffer> objects = new ArrayDeque<>();
    private final int payloadSizeBytes;
    private final int maxSize;

    public ReusableBufferPool(int maxSize, int payloadSizeBytes) {
        this.maxSize = maxSize;
        this.payloadSizeBytes = payloadSizeBytes;

        // Pre-filling the pool creates a long-lived object graph.
        // These objects are likely to survive young GC and move to old generation.
        for (int i = 0; i < maxSize; i++) {
            objects.addLast(new ReusableBuffer(payloadSizeBytes));
        }
    }

    public ReusableBuffer acquire() {
        ReusableBuffer buffer = objects.pollFirst();

        if (buffer == null) {
            // Pools often still allocate during bursts if the pool is undersized.
            return new ReusableBuffer(payloadSizeBytes);
        }

        return buffer;
    }

    public void release(ReusableBuffer buffer) {
        // Resetting is required for correctness.
        // It may reduce allocation pressure, but it adds CPU work.
        buffer.reset();

        if (objects.size() < maxSize) {
            objects.addLast(buffer);
        }

        // If the pool is full, the object is discarded.
        // This means pooling does not necessarily remove all allocations.
    }

    public int size() {
        return objects.size();
    }
}