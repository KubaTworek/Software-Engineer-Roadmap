package pl.jakubtworek.backend_systems_lab_stage_1.block_b.cpu_vs_io;

public final class ProfilingCaseStudyApp {

    public static void main(String[] args) throws Exception {
        // This case study creates two very different workloads:
        // - CPU-bound work that consumes processor time,
        // - wait-bound work that spends most time sleeping or waiting.
        //
        // The goal is to learn how to distinguish:
        // - hot CPU methods,
        // - blocked / waiting threads,
        // - wall-clock latency,
        // - actual CPU consumption.
        //
        // Run with JFR:
        // java -XX:StartFlightRecording=filename=profiling-case.jfr,duration=60s,settings=profile ProfilingCaseStudyApp

        ProfilingConfig config = ProfilingConfig.fromArgs(args);

        System.out.println("Starting profiling case study");
        System.out.println(config);

        Thread cpuThread = new Thread(new CpuBoundWorker(config), "cpu-bound-worker");
        Thread ioThread = new Thread(new WaitBoundWorker(config), "wait-bound-worker");
        Thread mixedThread = new Thread(new MixedWorker(config), "mixed-worker");

        cpuThread.start();
        ioThread.start();
        mixedThread.start();

        Thread.sleep(config.durationSeconds() * 1000L);

        cpuThread.interrupt();
        ioThread.interrupt();
        mixedThread.interrupt();

        cpuThread.join();
        ioThread.join();
        mixedThread.join();

        System.out.println("Profiling case study finished");
    }
}