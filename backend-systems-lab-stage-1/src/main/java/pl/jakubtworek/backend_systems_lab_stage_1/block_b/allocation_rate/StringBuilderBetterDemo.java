package pl.jakubtworek.backend_systems_lab_stage_1.block_b.allocation_rate;

public class StringBuilderBetterDemo {

    private static final int ITERATIONS = 5_000_000;

    public static void main(String[] args) {
        // This version uses one mutable StringBuilder.
        // It should create significantly less allocation churn than repeated String concatenation.

        long start = System.nanoTime();

        String result = buildWithStringBuilder();

        long end = System.nanoTime();

        // Printing the final length makes the result observable.
        System.out.println("Final length: " + result.length());
        System.out.println("Elapsed ms: " + (end - start) / 1_000_000);
    }

    private static String buildWithStringBuilder() {
        // StringBuilder stores characters in a growing internal buffer.
        // Instead of creating a brand new String on every iteration,
        // it mutates the existing builder state.
        //
        // Some reallocations may still happen when the internal buffer grows,
        // but the allocation rate is usually much lower than with repeated string concatenation.
        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < ITERATIONS; i++) {
            builder.append(i);
        }

        // One final String object is created here from the builder content.
        return builder.toString();
    }
}