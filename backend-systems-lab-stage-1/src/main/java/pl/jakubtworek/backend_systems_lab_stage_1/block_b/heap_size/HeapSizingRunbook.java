package pl.jakubtworek.backend_systems_lab_stage_1.block_b.heap_size;

public final class HeapSizingRunbook {

    public static void main(String[] args) {

        // This class documents recommended experiment runs.
        //
        // ============================================
        // CASE A — Dynamic heap resizing
        // ============================================
        //
        // Small initial heap, large maximum heap.
        // The JVM may repeatedly grow the heap under pressure.
        //
        // java
        //   -Xms256m
        //   -Xmx2g
        //   -XX:+UseG1GC
        //   -Xlog:gc*,gc+heap=debug:file=dynamic-heap.log:time,uptime,level,tags
        //   -XX:StartFlightRecording=filename=dynamic-heap.jfr,settings=profile
        //   HeapResizeExperiment
        //
        //
        // ============================================
        // CASE B — Fixed heap sizing
        // ============================================
        //
        // Initial heap equals maximum heap.
        // The JVM avoids runtime heap expansion.
        //
        // java
        //   -Xms2g
        //   -Xmx2g
        //   -XX:+UseG1GC
        //   -Xlog:gc*,gc+heap=debug:file=fixed-heap.log:time,uptime,level,tags
        //   -XX:StartFlightRecording=filename=fixed-heap.jfr,settings=profile
        //   HeapResizeExperiment
        //
        //
        // Compare:
        // - heap resizing activity,
        // - pause consistency,
        // - committed heap growth,
        // - GC frequency,
        // - startup footprint,
        // - memory reservation behavior.

        System.out.println("See source comments for experiment commands.");
    }
}