package pl.jakubtworek.backend_systems_lab_stage_1.block_b.object_pooling;

public final class PoolingRunbook {

    public static void main(String[] args) {
        // Compile:
        //
        // javac *.java
        //
        // Run with GC logs and JFR:
        //
        // java
        //   -Xms1g
        //   -Xmx1g
        //   -XX:+UseG1GC
        //   -Xlog:gc*,gc+heap=debug:file=object-pooling-gc.log:time,uptime,level,tags
        //   -XX:StartFlightRecording=filename=object-pooling.jfr,duration=90s,settings=profile
        //   ObjectPoolingCaseStudy
        //
        // Try different pool sizes:
        //
        // --poolSize=100
        // --poolSize=10000
        // --poolSize=100000
        //
        // Try different payload sizes:
        //
        // --payloadSizeBytes=64
        // --payloadSizeBytes=1024
        //
        // In JFR / GC logs compare:
        // - allocation rate,
        // - young GC frequency,
        // - old generation occupancy,
        // - object age / promotion behavior,
        // - monitor contention,
        // - CPU spent in reset/acquire/release,
        // - total throughput.
        //
        // Expected lesson:
        // pooling may reduce allocation rate,
        // but it can increase object lifetime, old-gen pressure,
        // synchronization cost, and code complexity.

        System.out.println("See source comments for run commands.");
    }
}