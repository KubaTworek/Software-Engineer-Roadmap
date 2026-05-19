package pl.jakubtworek.backend_systems_lab_stage_1.block_b.allocation_rate;

public class StringAllocationChurnDemo {

    private static final int ITERATIONS = 5_000_000;

    public static void main(String[] args) {
        // This demo intentionally creates many short-lived String objects.
        // The goal is to generate allocation churn visible in Java Flight Recorder.
        //
        // Run with JFR enabled, for example:
        // java -XX:StartFlightRecording=filename=string-allocation.jfr,duration=30s,settings=profile StringAllocationChurnDemo

        long start = System.nanoTime();

        String result = buildWithStringConcatenation();

        long end = System.nanoTime();

        // Printing the final length prevents the whole computation from being treated as unused.
        System.out.println("Final length: " + result.length());
        System.out.println("Elapsed ms: " + (end - start) / 1_000_000);
    }

    private static String buildWithStringConcatenation() {
        // This is the intentionally bad version.
        // Each loop iteration creates a new String based on the previous content.
        //
        // Conceptually:
        // result = result + i;
        //
        // means:
        // create temporary builder-like machinery,
        // copy existing characters,
        // append new characters,
        // create a new String.
        //
        // As the loop grows, this creates a large number of temporary allocations.
        String result = "";

        for (int i = 0; i < ITERATIONS; i++) {
            result = result + i;
        }

        return result;
    }
}