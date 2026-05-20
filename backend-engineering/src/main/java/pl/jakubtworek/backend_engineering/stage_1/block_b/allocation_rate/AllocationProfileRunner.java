package pl.jakubtworek.backend_engineering.stage_1.block_b.allocation_rate;

public class AllocationProfileRunner {

    public static void main(String[] args) {
        // This runner executes all variants in one process.
        // It is useful when comparing allocation profiles in a single JFR recording.
        //
        // Run with:
        // java -XX:StartFlightRecording=filename=allocation-profile.jfr,duration=60s,settings=profile AllocationProfileRunner

        run("String concatenation", AllocationProfileRunner::runConcatenation);
        run("StringBuilder", AllocationProfileRunner::runStringBuilder);
        run("Pre-sized StringBuilder", AllocationProfileRunner::runPreSizedStringBuilder);
    }

    private static void run(String name, Runnable task) {
        // The label helps correlate console output with JFR time ranges.
        System.out.println("Starting: " + name);

        long start = System.nanoTime();
        task.run();
        long end = System.nanoTime();

        System.out.println("Finished: " + name + " in " + (end - start) / 1_000_000 + " ms");
        System.out.println();
    }

    private static void runConcatenation() {
        String result = "";

        for (int i = 0; i < 200_000; i++) {
            // This intentionally creates many temporary String objects.
            // The lower iteration count prevents the demo from becoming excessively slow.
            result = result + i;
        }

        System.out.println("Length: " + result.length());
    }

    private static void runStringBuilder() {
        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < 5_000_000; i++) {
            // This version keeps mutable state in the builder.
            // It should allocate much less frequently than repeated concatenation.
            builder.append(i);
        }

        System.out.println("Length: " + builder.length());
    }

    private static void runPreSizedStringBuilder() {
        StringBuilder builder = new StringBuilder(5_000_000 * 7);

        for (int i = 0; i < 5_000_000; i++) {
            // Pre-sizing reduces internal buffer growth.
            builder.append(i);
        }

        System.out.println("Length: " + builder.length());
    }
}