package pl.jakubtworek.backend_systems_lab_stage_1.block_b.g1_vs_zgc;

public final class Payload {

    private final byte[] data;
    private final long createdAtNanos;

    public Payload(int sizeBytes) {
        // The byte array is the main allocation payload.
        // It makes allocation pressure visible in GC logs and JFR.
        this.data = new byte[sizeBytes];

        // A timestamp makes the object slightly more realistic
        // and prevents the class from being completely trivial.
        this.createdAtNanos = System.nanoTime();
    }

    public int touch() {
        // Touching the array makes the payload observable.
        // This helps prevent overly aggressive dead-code elimination.
        data[0] = (byte) createdAtNanos;
        return data[0];
    }
}