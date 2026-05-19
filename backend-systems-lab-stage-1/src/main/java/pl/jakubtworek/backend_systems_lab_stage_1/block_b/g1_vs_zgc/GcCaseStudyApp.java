package pl.jakubtworek.backend_systems_lab_stage_1.block_b.g1_vs_zgc;

public class GcCaseStudyApp {

    public static void main(String[] args) throws Exception {
        // This app simulates a mixed workload:
        // - steady request processing,
        // - short-lived allocations,
        // - medium-lived objects,
        // - a growing live set,
        // - occasional latency-sensitive operations.
        //
        // The goal is to compare GC behavior under G1 and ZGC,
        // especially throughput, pause times, allocation pressure, and heap behavior.

        WorkloadConfig config = WorkloadConfig.fromArgs(args);

        System.out.println("Starting GC case study");
        System.out.println(config);

        LiveSet liveSet = new LiveSet(config.liveSetTargetMb());
        AllocationWorker allocationWorker = new AllocationWorker(config, liveSet);
        LatencyProbe latencyProbe = new LatencyProbe(config);

        Thread workerThread = new Thread(allocationWorker, "allocation-worker");
        Thread latencyThread = new Thread(latencyProbe, "latency-probe");

        workerThread.start();
        latencyThread.start();

        Thread.sleep(config.durationSeconds() * 1000L);

        allocationWorker.stop();
        latencyProbe.stop();

        workerThread.join();
        latencyThread.join();

        System.out.println("Case study finished");
    }
}