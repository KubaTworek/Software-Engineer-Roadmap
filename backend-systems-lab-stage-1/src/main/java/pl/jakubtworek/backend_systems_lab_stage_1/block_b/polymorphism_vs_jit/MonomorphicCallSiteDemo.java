package pl.jakubtworek.backend_systems_lab_stage_1.block_b.polymorphism_vs_jit;

public class MonomorphicCallSiteDemo {

    private static final int ITERATIONS = 100_000_000;

    public static void main(String[] args) {
        // This call-site is monomorphic.
        // The JVM sees only one concrete implementation type.
        //
        // HotSpot can usually:
        // - devirtualize the call,
        // - inline the target method,
        // - optimize aggressively afterward.
        Operation operation = new AddOperation();

        long sum = 0;

        long start = System.nanoTime();

        for (int i = 0; i < ITERATIONS; i++) {
            sum += operation.apply(i);
        }

        long end = System.nanoTime();

        System.out.println("Result: " + sum);
        System.out.println("Elapsed ms: " + (end - start) / 1_000_000);
    }
}