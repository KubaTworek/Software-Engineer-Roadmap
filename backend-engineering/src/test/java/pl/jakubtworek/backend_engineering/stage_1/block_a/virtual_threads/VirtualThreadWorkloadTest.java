package pl.jakubtworek.backend_engineering.stage_1.block_a.virtual_threads;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class VirtualThreadWorkloadTest {

    @Test
    void virtualThreadsLetBlockingTasksStartWithoutAPlatformPoolQueue() throws Exception {
        BlockingConcurrencyComparison.Comparison comparison =
                new BlockingConcurrencyComparison().compare(30, 3, Duration.ofSeconds(2));

        assertThat(comparison.platformThreads().startedWhileBlocked()).isEqualTo(3);
        assertThat(comparison.platformThreads().maximumActiveTasks()).isEqualTo(3);
        assertThat(comparison.platformThreads().everyStartedTaskWasVirtual()).isFalse();

        assertThat(comparison.virtualThreads().startedWhileBlocked()).isEqualTo(30);
        assertThat(comparison.virtualThreads().maximumActiveTasks()).isEqualTo(30);
        assertThat(comparison.virtualThreads().everyStartedTaskWasVirtual()).isTrue();
    }

    @Test
    void threadTypeDoesNotChangeTheResultOfCpuBoundWork() throws Exception {
        CpuBoundWorkload workload = new CpuBoundWorkload();
        List<Integer> inputs = List.of(1_000, 2_000, 3_000, 4_000);

        List<Integer> platformResult;
        List<Integer> virtualResult;
        try (ExecutorService platform = Executors.newFixedThreadPool(4);
             ExecutorService virtual = Executors.newVirtualThreadPerTaskExecutor()) {
            platformResult = workload.countPrimes(platform, inputs);
            virtualResult = workload.countPrimes(virtual, inputs);
        }

        assertThat(virtualResult).containsExactlyElementsOf(platformResult);
        assertThat(virtualResult).containsExactly(168, 303, 430, 550);
    }

    @Test
    void semaphoreProtectsAConstrainedDownstreamFromManyVirtualThreads() throws Exception {
        int databaseConnections = 3;
        int requests = 30;
        BoundedDownstream database = new BoundedDownstream(databaseConnections);
        CountDownLatch allConnectionsOccupied = new CountDownLatch(databaseConnections);
        CountDownLatch releaseDatabase = new CountDownLatch(1);
        List<Future<String>> results = new ArrayList<>();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int request = 0; request < requests; request++) {
                results.add(executor.submit(() -> database.execute(() -> {
                    allConnectionsOccupied.countDown();
                    releaseDatabase.await();
                    return "ok";
                })));
            }

            assertThat(allConnectionsOccupied.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(database.activeOperations()).isEqualTo(databaseConnections);
            assertThat(database.maximumObservedConcurrency()).isEqualTo(databaseConnections);
            releaseDatabase.countDown();

            for (Future<String> result : results) {
                assertThat(result.get()).isEqualTo("ok");
            }
        } finally {
            releaseDatabase.countDown();
        }

        assertThat(database.maximumObservedConcurrency()).isEqualTo(databaseConnections);
    }
}
