package pl.jakubtworek.backend_systems_lab_stage_1.block_b.cpu_vs_io;

public final class JfrCommandRunbook {

    public static void main(String[] args) {
        // This class documents useful JFR commands.
        //
        // Compile:
        //
        // javac *.java
        //
        // Start application without initial recording:
        //
        // java ProfilingCaseStudyApp
        //
        // Find JVM process:
        //
        // jcmd
        //
        // Start JFR recording dynamically:
        //
        // jcmd <pid> JFR.start name=profile settings=profile filename=profiling-case.jfr
        //
        // Check active recordings:
        //
        // jcmd <pid> JFR.check
        //
        // Dump recording:
        //
        // jcmd <pid> JFR.dump name=profile filename=profiling-case.jfr
        //
        // Stop recording:
        //
        // jcmd <pid> JFR.stop name=profile
        //
        // Alternative one-shot startup recording:
        //
        // java
        //   -XX:StartFlightRecording=filename=profiling-case.jfr,duration=60s,settings=profile
        //   ProfilingCaseStudyApp
        //
        // In JDK Mission Control, inspect:
        // - Method Profiling,
        // - Hot Methods,
        // - Threads,
        // - Thread States,
        // - Java Thread Statistics,
        // - Events related to sleep / park / wait.
        //
        // Interpretation goal:
        // - CPU-bound code dominates CPU samples.
        // - Wait-bound code dominates wall-clock time but not CPU samples.
        // - Thread states explain where time is actually spent.

        System.out.println("See source comments for JFR and jcmd commands.");
    }
}