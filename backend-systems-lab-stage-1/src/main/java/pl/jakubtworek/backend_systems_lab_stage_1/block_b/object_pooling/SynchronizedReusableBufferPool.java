package pl.jakubtworek.backend_systems_lab_stage_1.block_b.object_pooling;

import java.util.ArrayDeque;

public final class SynchronizedReusableBufferPool {

    private final ArrayDeque<ReusableBuffer> objects = new ArrayDeque<>();
    private final int payloadSizeBytes;
    private final int maxSize;

    public SynchronizedReusableBufferPool(int maxSize, int payloadSizeBytes) {
        this.maxSize = maxSize;
        this.payloadSizeBytes = payloadSizeBytes;

        for (int i = 0; i < maxSize; i++) {
            objects.addLast(new ReusableBuffer(payloadSizeBytes));
        }
    }

    public synchronized ReusableBuffer acquire() {
        // This synchronized method protects pool state.
        // Under many threads, the pool itself can become a scalability bottleneck.
        ReusableBuffer buffer = objects.pollFirst();

        if (buffer == null) {
            return new ReusableBuffer(payloadSizeBytes);
        }

        return buffer;
    }

    public synchronized void release(ReusableBuffer buffer) {
        // Reset is done while holding the monitor in this simple implementation.
        // This intentionally demonstrates how naive pooling can amplify contention.
        buffer.reset();

        if (objects.size() < maxSize) {
            objects.addLast(buffer);
        }
    }

    public synchronized int size() {
        return objects.size();
    }
}