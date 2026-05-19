package pl.jakubtworek.backend_systems_lab_stage_1.block_b.g1_vs_zgc;

public final class GcLogRunBook {

    public static void main(String[] args) {
        // This class intentionally does not run the workload.
        // It documents the commands used in the case study.
        //
        // G1 example:
        //
        // java -Xms2g -Xmx2g
        //      -XX:+UseG1GC
        //      -Xlog:gc*,safepoint:file=g1-gc.log:time,uptime,level,tags
        //      -XX:StartFlightRecording=filename=g1.jfr,settings=profile,duration=120s
        //      GcCaseStudyApp
        //
        // ZGC example:
        //
        // java -Xms2g -Xmx2g
        //      -XX:+UseZGC
        //      -Xlog:gc*,safepoint:file=zgc-gc.log:time,uptime,level,tags
        //      -XX:StartFlightRecording=filename=zgc.jfr,settings=profile,duration=120s
        //      GcCaseStudyApp
        //
        // Compare:
        // - pause times,
        // - allocation rate,
        // - CPU usage,
        // - live set size,
        // - heap occupancy,
        // - GC frequency,
        // - tail latency from LatencyProbe,
        // - JFR allocation and GC events.

        System.out.println("See source comments for G1 and ZGC run commands.");
    }
}