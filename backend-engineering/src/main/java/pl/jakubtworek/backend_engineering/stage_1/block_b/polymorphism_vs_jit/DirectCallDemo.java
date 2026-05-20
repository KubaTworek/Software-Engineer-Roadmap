package pl.jakubtworek.backend_engineering.stage_1.block_b.polymorphism_vs_jit;

public class DirectCallDemo {

    private static final int ITERATIONS = 100_000_000;

    public static void main(String[] args) {
        // This is a direct concrete call.
        // No virtual dispatch is needed.
        //
        // This is the easiest possible case for the JIT.
        AddOperation operation = new AddOperation();

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