package pl.jakubtworek.backend_engineering.stage_1.block_b.object_pooling;

import java.util.Arrays;

public final class ReusableBuffer {

    private final byte[] data;
    private int length;

    public ReusableBuffer(int capacity) {
        // This byte array represents the expensive payload.
        // Pooling this object keeps both the wrapper and internal array alive.
        this.data = new byte[capacity];
    }

    public void write(int seed) {
        // This simulates request-local temporary data.
        // The object must be reset before it is reused.
        length = data.length;

        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (seed + i);
        }
    }

    public long checksum() {
        long result = 0;

        for (int i = 0; i < length; i += 16) {
            result += data[i];
        }

        return result;
    }

    public void reset() {
        // Resetting pooled objects is easy to forget.
        // Missing reset can cause stale data bugs.
        //
        // Clearing memory also has a CPU cost.
        Arrays.fill(data, (byte) 0);
        length = 0;
    }
}