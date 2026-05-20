package pl.jakubtworek.backend_engineering.stage_1.block_b.polymorphism_vs_jit;

public class MegamorphicCallSiteDemo {

    private static final int ITERATIONS = 100_000_000;

    public static void main(String[] args) {
        // This call-site is intentionally megamorphic.
        // Many implementation types flow through the same virtual call-site.
        //
        // Megamorphic call-sites are much harder for the JIT to optimize.
        // Devirtualization and inlining often fail here.
        Operation[] operations = {
                new AddOperation(),
                new MultiplyOperation(),
                new SquareOperation(),
                new ModOperation()
        };

        long sum = 0;

        long start = System.nanoTime();

        for (int i = 0; i < ITERATIONS; i++) {
            // Multiple runtime types appear at the same call-site.
            Operation operation = operations[i & 3];

            sum += operation.apply(i);
        }

        long end = System.nanoTime();

        System.out.println("Result: " + sum);
        System.out.println("Elapsed ms: " + (end - start) / 1_000_000);
    }
}