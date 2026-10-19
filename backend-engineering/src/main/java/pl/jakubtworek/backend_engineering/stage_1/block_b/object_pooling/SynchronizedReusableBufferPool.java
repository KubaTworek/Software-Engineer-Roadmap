package pl.jakubtworek.backend_engineering.stage_1.block_b.object_pooling;

import java.util.ArrayDeque;
import java.util.Objects;

public final class SynchronizedReusableBufferPool {

    private final ArrayDeque<ReusableBuffer> objects = new ArrayDeque<>();
    private final int payloadSizeBytes;
    private final int maxSize;

    public SynchronizedReusableBufferPool(int maxSize, int payloadSizeBytes) {
        if (maxSize < 0) {
            throw new IllegalArgumentException("maxSize must not be negative");
        }
        if (payloadSizeBytes <= 0) {
            throw new IllegalArgumentException("payloadSizeBytes must be greater than zero");
        }
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
        Objects.requireNonNull(buffer, "buffer must not be null").reset();

        if (objects.size() < maxSize) {
            objects.addLast(buffer);
        }
    }

    public synchronized int size() {
        return objects.size();
    }
}
