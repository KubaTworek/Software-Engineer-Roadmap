package pl.jakubtworek.backend_engineering.stage_1.block_b.lock_contention;

public final class JfrRunbook {

    public static void main(String[] args) {
        // This class documents useful run commands.
        //
        // Compile:
        //
        // javac *.java
        //
        // Run with JFR:
        //
        // java
        //   -XX:StartFlightRecording=filename=lock-contention.jfr,duration=70s,settings=profile
        //   LockContentionCaseStudy
        //
        // Run with explicit thread count:
        //
        // java
        //   -XX:StartFlightRecording=filename=lock-contention-32t.jfr,duration=70s,settings=profile
        //   LockContentionCaseStudy
        //   --threads=32
        //
        // In JDK Mission Control, inspect:
        // - Java Monitor Blocked,
        // - Thread Park,
        // - CPU usage,
        // - Hot Methods,
        // - Method Profiling,
        // - Object Allocation if the implementation changes.
        //
        // Expected high-level behavior:
        // - synchronized may show monitor contention,
        // - AtomicLong may burn CPU on CAS retries,
        // - LongAdder should usually scale better for frequent increments.

        System.out.println("See source comments for JFR run commands.");
    }
}