package pl.jakubtworek.backend_engineering.stage_1.block_b.escape_analysis;

public final class Point {

    // Immutable fields make the object easier for the JIT to reason about.
    private final int x;
    private final int y;

    public Point(int x, int y) {
        // Constructor parameters are local variables.
        // Conceptually, the new Point object is allocated on the heap.
        // In optimized code, Escape Analysis may prove that this allocation is unnecessary.
        this.x = x;
        this.y = y;
    }

    public int sum() {
        return x + y;
    }
}