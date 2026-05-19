package pl.jakubtworek.backend_systems_lab_stage_1.block_b.polymorphism_vs_jit;

public class BimorphicCallSiteDemo {

    private static final int ITERATIONS = 100_000_000;

    public static void main(String[] args) {
        // This call-site is bimorphic.
        // The JVM observes two implementation types.
        //
        // HotSpot can still optimize many bimorphic call-sites,
        // often using type checks and guarded inlining.
        Operation[] operations = {
                new AddOperation(),
                new MultiplyOperation()
        };

        long sum = 0;

        long start = System.nanoTime();

        for (int i = 0; i < ITERATIONS; i++) {
            Operation operation = operations[i & 1];

            sum += operation.apply(i);
        }

        long end = System.nanoTime();

        System.out.println("Result: " + sum);
        System.out.println("Elapsed ms: " + (end - start) / 1_000_000);
    }
}