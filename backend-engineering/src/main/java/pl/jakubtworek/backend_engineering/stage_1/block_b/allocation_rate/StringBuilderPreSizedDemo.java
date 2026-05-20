package pl.jakubtworek.backend_engineering.stage_1.block_b.allocation_rate;

public class StringBuilderPreSizedDemo {

    private static final int ITERATIONS = 5_000_000;

    public static void main(String[] args) {
        // This version pre-sizes the StringBuilder.
        // The goal is to reduce internal buffer resizing and copying.

        long start = System.nanoTime();

        String result = buildWithPreSizedStringBuilder();

        long end = System.nanoTime();

        System.out.println("Final length: " + result.length());
        System.out.println("Elapsed ms: " + (end - start) / 1_000_000);
    }

    private static String buildWithPreSizedStringBuilder() {
        // This is only a rough estimate.
        // The exact number of characters depends on how many digits each number has.
        int estimatedCapacity = ITERATIONS * 7;

        // Providing capacity upfront can reduce the number of internal char[] / byte[] reallocations.
        StringBuilder builder = new StringBuilder(estimatedCapacity);

        for (int i = 0; i < ITERATIONS; i++) {
            builder.append(i);
        }

        return builder.toString();
    }
}