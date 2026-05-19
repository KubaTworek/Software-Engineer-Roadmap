package pl.jakubtworek.backend_systems_lab_stage_1.block_a.threads.executor_service;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.*;

public class ExecutorExperiment {

    public static void main(String[] args) throws InterruptedException {

        TaskProcessor processor = new TaskProcessor();
        int tasks = 10_000; // number of tasks submitted to the executor

        // Experiment 1: cached thread pool
        // Expectation: pool may grow significantly because there is no queue
        System.out.println("==== Cached Thread Pool ====");
        runExperiment((ThreadPoolExecutor) ExecutorConfigurations.cachedPool(), processor, tasks);

        System.out.println();
        // Experiment 2: fixed thread pool with 10 workers
        // Expectation: only 10 tasks will run concurrently, the rest will wait in the queue
        System.out.println("==== Fixed Thread Pool (10) ====");
        runExperiment((ThreadPoolExecutor) ExecutorConfigurations.fixedPool(10), processor, tasks);
    }

    private static void runExperiment(ThreadPoolExecutor executor,
                                      TaskProcessor processor,
                                      int tasks) throws InterruptedException {

        // ThreadMXBean allows inspecting JVM thread statistics
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();

        // Baseline measurements before tasks are submitted
        long memoryBefore = usedMemory();              // memory used by JVM
        long threadCountBefore = threadBean.getThreadCount(); // total live threads

        long start = System.currentTimeMillis();

        // Submit tasks to the executor (actual workload)
        processor.processTasks(executor, tasks);

        long end = System.currentTimeMillis();

        // Measurements after tasks finish
        long memoryAfter = usedMemory();
        long threadCountAfter = threadBean.getThreadCount();

        // Total time needed to process all tasks
        System.out.println("Time: " + (end - start) + " ms");

        // Highest number of threads that existed simultaneously in the pool
        // Useful for observing how much the pool expanded
        System.out.println("Largest pool size: " + executor.getLargestPoolSize());

        // Number of tasks that completed execution
        // Should equal the submitted task count if no task was rejected
        System.out.println("Completed tasks: " + executor.getCompletedTaskCount());

        // Difference in used memory before and after the experiment
        // Helps observe memory pressure caused by thread creation or queued tasks
        System.out.println("Memory delta: " + (memoryAfter - memoryBefore) / 1024 + " KB");

        // Difference in the total number of JVM threads
        // Shows how many additional threads were created during execution
        System.out.println("Thread count delta: " + (threadCountAfter - threadCountBefore));
    }

    /**
     * Returns currently used heap memory.
     * Calculation:
     * totalMemory - freeMemory
     *
     * Used only for rough comparison between executor configurations.
     */
    private static long usedMemory() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }
}