package pl.jakubtworek.booking.service.profiling;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.booking.dto.ReservationCreateRequest;
import pl.jakubtworek.booking.dto.profiling.AllocationPressureResponse;
import pl.jakubtworek.booking.dto.profiling.LockContentionResponse;
import pl.jakubtworek.booking.dto.profiling.OrganizationReportProfilingResponse;
import pl.jakubtworek.booking.dto.profiling.ProfilingReservationResponse;
import pl.jakubtworek.booking.dto.profiling.ProfilingRunResponse;
import pl.jakubtworek.booking.dto.profiling.ThreadPoolExperimentResponse;
import pl.jakubtworek.booking.exception.CapacityUnavailableException;
import pl.jakubtworek.booking.service.ReservationService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class JvmProfilingService {
    private final ReservationService reservationService;
    private final JdbcTemplate jdbcTemplate;
    private final Object contendedLock = new Object();
    private long contendedCounter;

    public JvmProfilingService(ReservationService reservationService, JdbcTemplate jdbcTemplate) {
        this.reservationService = reservationService;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Scenario: many short DB-backed reservations.
     * Observe: JDBC calls, transaction overhead, allocation rate around DTO/entity creation, DB latency.
     */
    public ProfilingReservationResponse runShortReservationBurst(UUID eventId, int requestedReservations) {
        long start = System.nanoTime();
        int successful = 0;
        int failed = 0;
        for (int i = 0; i < requestedReservations; i++) {
            try {
                reservationService.create(eventId, new ReservationCreateRequest(
                        "Profiling User " + i,
                        "profiling-short-" + UUID.randomUUID() + "@example.com"
                ));
                successful++;
            } catch (CapacityUnavailableException ex) {
                failed++;
            }
        }
        long elapsed = System.nanoTime() - start;
        double throughput = requestedReservations * 1_000_000_000.0 / Math.max(1L, elapsed);
        return new ProfilingReservationResponse(
                eventId,
                requestedReservations,
                successful,
                failed,
                Math.max(1L, elapsed / 1_000_000L),
                throughput,
                "JFR: JDBC latency, transaction cost, allocation rate, GC pauses"
        );
    }

    /**
     * Scenario: many concurrent requests against one event.
     * Observe: thread states, lock/DB contention, connection pool pressure, latency distribution.
     */
    public ProfilingReservationResponse runParallelReservationBurst(UUID eventId, int requestedReservations, int threads) {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startGate = new CountDownLatch(1);
        AtomicLong successful = new AtomicLong();
        AtomicLong failed = new AtomicLong();
        List<Future<?>> futures = new ArrayList<>();

        long start = System.nanoTime();
        for (int i = 0; i < requestedReservations; i++) {
            int index = i;
            futures.add(executor.submit(() -> {
                await(startGate);
                try {
                    reservationService.create(eventId, new ReservationCreateRequest(
                            "Parallel Profiling User " + index,
                            "profiling-parallel-" + UUID.randomUUID() + "@example.com"
                    ));
                    successful.incrementAndGet();
                } catch (CapacityUnavailableException ex) {
                    failed.incrementAndGet();
                }
            }));
        }
        startGate.countDown();
        waitFor(futures);
        shutdown(executor);
        long elapsed = System.nanoTime() - start;
        double throughput = requestedReservations * 1_000_000_000.0 / Math.max(1L, elapsed);
        return new ProfilingReservationResponse(
                eventId,
                requestedReservations,
                successful.intValue(),
                failed.intValue(),
                Math.max(1L, elapsed / 1_000_000L),
                throughput,
                "JFR/VisualVM: runnable vs blocked threads, DB locks, connection pool wait, latency"
        );
    }

    /**
     * Scenario: report generated for organization.
     * Observe: SQL plan, hash aggregate, sort, index usage, allocation in mapping result set.
     */
    @Transactional(readOnly = true)
    public OrganizationReportProfilingResponse organizationReport(UUID organizationId) {
        long start = System.nanoTime();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                select status, count(*) as cnt
                  from reservations
                 where organization_id = ?
                 group by status
                 order by status
                """, organizationId);
        Map<String, Long> byStatus = new HashMap<>();
        long total = 0;
        for (Map<String, Object> row : rows) {
            long count = ((Number) row.get("CNT")).longValue();
            byStatus.put(String.valueOf(row.get("STATUS")), count);
            total += count;
        }
        long elapsed = System.nanoTime() - start;
        return new OrganizationReportProfilingResponse(
                organizationId,
                total,
                byStatus,
                Math.max(1L, elapsed / 1_000_000L),
                "PostgreSQL EXPLAIN ANALYZE: index scan vs sequential scan, hash aggregate, sort"
        );
    }

    /**
     * Scenario: deliberately allocation-heavy endpoint.
     * Observe: allocation rate, young GC frequency, escape analysis limits.
     */
    public AllocationPressureResponse allocationPressure(int objects) {
        long start = System.nanoTime();
        List<String> payload = new ArrayList<>(objects);
        long approximateBytes = 0;
        for (int i = 0; i < objects; i++) {
            String value = "allocation-pressure-" + i + "-" + UUID.randomUUID() + "-" + Instant.now();
            payload.add(value);
            approximateBytes += value.length() * 2L;
        }
        // Keep the list live until here so profiler can see retained objects during the method.
        if (payload.size() != objects) {
            throw new IllegalStateException("Unexpected allocation scenario result");
        }
        long elapsed = System.nanoTime() - start;
        return new AllocationPressureResponse(
                objects,
                approximateBytes,
                Math.max(1L, elapsed / 1_000_000L),
                "JFR: allocation in new TLAB/outside TLAB, young GC pauses, allocation hotspots"
        );
    }

    /**
     * Scenario: many threads competing for one monitor.
     * Observe: monitor blocked time and low throughput caused by lock contention.
     */
    public LockContentionResponse lockContention(int threads, int incrementsPerThread) {
        contendedCounter = 0;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Future<?>> futures = new ArrayList<>();
        CountDownLatch startGate = new CountDownLatch(1);
        long start = System.nanoTime();
        for (int t = 0; t < threads; t++) {
            futures.add(executor.submit(() -> {
                await(startGate);
                for (int i = 0; i < incrementsPerThread; i++) {
                    synchronized (contendedLock) {
                        contendedCounter++;
                    }
                }
            }));
        }
        startGate.countDown();
        waitFor(futures);
        shutdown(executor);
        long elapsed = System.nanoTime() - start;
        return new LockContentionResponse(
                threads,
                incrementsPerThread,
                contendedCounter,
                Math.max(1L, elapsed / 1_000_000L),
                "JFR: Java Monitor Blocked, lock owner thread, contention duration"
        );
    }

    /**
     * Scenario: compare thread count for CPU-bound or IO-bound tasks.
     * Observe: context switching, CPU saturation, waiting threads, throughput curve.
     */
    public ThreadPoolExperimentResponse threadPoolExperiment(int threads, int tasks, String workloadType) {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Callable<Long>> callables = new ArrayList<>();
        for (int i = 0; i < tasks; i++) {
            callables.add(() -> {
                if ("IO".equalsIgnoreCase(workloadType)) {
                    Thread.sleep(25);
                    return 1L;
                }
                return cpuWork();
            });
        }
        long start = System.nanoTime();
        try {
            executor.invokeAll(callables);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Thread pool experiment interrupted", ex);
        } finally {
            shutdown(executor);
        }
        long elapsed = System.nanoTime() - start;
        double throughput = tasks * 1_000_000_000.0 / Math.max(1L, elapsed);
        return new ThreadPoolExperimentResponse(
                threads,
                tasks,
                workloadType,
                Math.max(1L, elapsed / 1_000_000L),
                throughput,
                "CPU workload: CPU saturation/context switches. IO workload: waiting threads/throughput scaling."
        );
    }

    public ProfilingRunResponse numericAllocationScenario(int iterations) {
        long start = System.nanoTime();
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < iterations; i++) {
            sum = sum.add(BigDecimal.valueOf(i).movePointLeft(2));
        }
        if (sum.signum() < 0) {
            throw new IllegalStateException("Impossible result");
        }
        long elapsed = System.nanoTime() - start;
        return ProfilingRunResponse.of(
                "BigDecimal allocation scenario",
                iterations,
                elapsed,
                "JFR: BigDecimal allocation rate; compare with JMH long-money benchmark"
        );
    }

    private long cpuWork() {
        long x = 0;
        for (int i = 0; i < 75_000; i++) {
            x += (i * 31L) ^ (x >>> 3);
        }
        return x;
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for profiling scenario", ex);
        }
    }

    private void waitFor(List<Future<?>> futures) {
        for (Future<?> future : futures) {
            try {
                future.get(30, TimeUnit.SECONDS);
            } catch (Exception ex) {
                throw new IllegalStateException("Profiling task failed", ex);
            }
        }
    }

    private void shutdown(ExecutorService executor) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException ex) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
