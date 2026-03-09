package pl.jakubtworek.backend_systems_lab_stage_1.block_a.threads.executor_service;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.*;

public class ExecutorExperiment {

    public static void main(String[] args) throws InterruptedException {

        TaskProcessor processor = new TaskProcessor();
        int tasks = 10_000;

        System.out.println("==== Cached Thread Pool ====");
        runExperiment((ThreadPoolExecutor) ExecutorConfigurations.cachedPool(), processor, tasks);

        System.out.println();
        System.out.println("==== Fixed Thread Pool (10) ====");
        runExperiment((ThreadPoolExecutor) ExecutorConfigurations.fixedPool(10), processor, tasks);
    }

    private static void runExperiment(ThreadPoolExecutor executor,
                                      TaskProcessor processor,
                                      int tasks) throws InterruptedException {

        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();

        long memoryBefore = usedMemory();
        long threadCountBefore = threadBean.getThreadCount();

        long start = System.currentTimeMillis();

        processor.processTasks(executor, tasks);

        long end = System.currentTimeMillis();

        long memoryAfter = usedMemory();
        long threadCountAfter = threadBean.getThreadCount();

        System.out.println("Time: " + (end - start) + " ms");
        System.out.println("Largest pool size: " + executor.getLargestPoolSize());
        System.out.println("Completed tasks: " + executor.getCompletedTaskCount());
        System.out.println("Memory delta: " + (memoryAfter - memoryBefore) / 1024 + " KB");
        System.out.println("Thread count delta: " + (threadCountAfter - threadCountBefore));
    }

    private static long usedMemory() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }
}