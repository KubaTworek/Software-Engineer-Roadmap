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

/**
 * Serwis z kontrolowanymi scenariuszami do profilowania JVM i aplikacji.
 *
 * To nie jest typowy serwis biznesowy.
 *
 * Jego zadaniem jest wygenerowanie takich sytuacji, które da się obserwować
 * w narzędziach profilujących:
 *
 * - dużo krótkich operacji z bazą,
 * - wiele równoległych rezerwacji,
 * - raport z agregacją SQL,
 * - duża liczba alokacji,
 * - lock contention,
 * - różne rozmiary puli wątków,
 * - koszt BigDecimal.
 *
 * Wyniki zwracane przez metody są tylko orientacyjne.
 * Prawdziwa analiza powinna opierać się na JFR, profilerze, GC logach,
 * JMH albo EXPLAIN ANALYZE.
 */
@Service
public class JvmProfilingService {

    /**
     * Główny serwis rezerwacji.
     *
     * Używany w scenariuszach, które mają przejść przez realny flow aplikacji:
     * - transakcje,
     * - repozytoria,
     * - SQL,
     * - tworzenie DTO,
     * - atomic update dostępności.
     */
    private final ReservationService reservationService;

    /**
     * JdbcTemplate jest używany do prostych zapytań raportowych.
     *
     * W scenariuszach profilingowych czasem lepiej użyć jawnego SQL,
     * żeby łatwiej analizować plan wykonania i uniknąć narzutu JPA tam,
     * gdzie nie jest potrzebny.
     */
    private final JdbcTemplate jdbcTemplate;

    /**
     * Wspólny lock używany do celowego wygenerowania contention.
     *
     * Wiele wątków będzie próbowało wejść do synchronized na tym samym obiekcie.
     */
    private final Object contendedLock = new Object();

    /**
     * Licznik chroniony przez contendedLock.
     *
     * Jest zwykłym longiem, bo celem scenariusza jest właśnie pokazanie kosztu
     * synchronizacji na jednym monitorze.
     */
    private long contendedCounter;

    /**
     * Constructor injection.
     */
    public JvmProfilingService(ReservationService reservationService, JdbcTemplate jdbcTemplate) {
        this.reservationService = reservationService;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Scenariusz: dużo krótkich rezerwacji wykonywanych jedna po drugiej.
     *
     * Co obserwować:
     *
     * - czas transakcji,
     * - liczbę zapytań JDBC,
     * - latency bazy danych,
     * - allocation rate przy tworzeniu DTO/encji,
     * - young GC,
     * - koszt walidacji i mapowania.
     *
     * Ten scenariusz jest prosty, ale dobry jako baseline przed testami równoległymi.
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
                /*
                 * Brak miejsc nie jest awarią scenariusza profilingowego.
                 * To oczekiwany rezultat, jeśli liczba requestów przekracza dostępność.
                 */
                failed++;
            }
        }

        long elapsed = System.nanoTime() - start;

        /*
         * Throughput liczony orientacyjnie jako requesty na sekundę.
         *
         * To nie zastępuje benchmarku. Pomiar w aplikacji webowej jest podatny
         * na wiele czynników: JIT, GC, bazę, connection pool, stan danych.
         */
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
     * Scenariusz: wiele równoległych prób rezerwacji dla jednego eventu.
     *
     * Co obserwować:
     *
     * - contention na bazie danych,
     * - zachowanie atomowego update'u dostępności,
     * - liczbę wątków RUNNABLE/BLOCKED/WAITING,
     * - connection pool pressure,
     * - wpływ liczby wątków na throughput,
     * - latency ogonowe.
     */
    public ProfilingReservationResponse runParallelReservationBurst(UUID eventId,
                                                                    int requestedReservations,
                                                                    int threads) {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startGate = new CountDownLatch(1);

        AtomicLong successful = new AtomicLong();
        AtomicLong failed = new AtomicLong();

        List<Future<?>> futures = new ArrayList<>();

        long start = System.nanoTime();

        for (int i = 0; i < requestedReservations; i++) {
            int index = i;

            futures.add(executor.submit(() -> {
                /*
                 * Wszystkie zadania czekają na wspólną bramkę startową.
                 * Dzięki temu startują możliwie równocześnie, co zwiększa szansę
                 * na realną konkurencję.
                 */
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

        /*
         * Otwieramy bramkę i pozwalamy wątkom ruszyć.
         */
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
     * Scenariusz: raport dla organizacji.
     *
     * Zapytanie liczy rezerwacje pogrupowane po statusie.
     *
     * Co obserwować:
     *
     * - czy baza używa indeksu po organization_id,
     * - czy robi sequential scan,
     * - koszt hash aggregate,
     * - koszt sortowania,
     * - allocation przy mapowaniu wyniku.
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
            /*
             * Uwaga: w zależności od bazy/drivera nazwy kolumn w Map mogą być
             * uppercase albo lowercase. Tutaj użyto "CNT" i "STATUS".
             *
             * Jeśli po zmianie bazy dostaniesz null, sprawdź klucze w rows.
             */
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
     * Scenariusz: celowo duża liczba krótkotrwałych alokacji.
     *
     * Co obserwować:
     *
     * - allocation rate,
     * - alokacje w TLAB / poza TLAB,
     * - young GC,
     * - wpływ tworzenia Stringów, UUID i Instant.
     */
    public AllocationPressureResponse allocationPressure(int objects) {
        long start = System.nanoTime();

        List<String> payload = new ArrayList<>(objects);
        long approximateBytes = 0;

        for (int i = 0; i < objects; i++) {
            String value = "allocation-pressure-" + i + "-" + UUID.randomUUID() + "-" + Instant.now();
            payload.add(value);

            /*
             * Przybliżenie rozmiaru samych znaków.
             *
             * To nie jest dokładny rozmiar obiektu w heapie.
             * String ma dodatkowy narzut obiektu, tablicy/byte array, alignment itd.
             */
            approximateBytes += value.length() * 2L;
        }

        /*
         * Celowo trzymamy listę żywą do tego miejsca.
         *
         * Dzięki temu profiler może zobaczyć retencję obiektów w trakcie metody.
         */
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
     * Scenariusz: lock contention.
     *
     * Wiele wątków konkuruje o ten sam monitor synchronized.
     *
     * Co obserwować:
     *
     * - Java Monitor Blocked w JFR,
     * - czas oczekiwania na monitor,
     * - spadek throughputu,
     * - wpływ liczby wątków na contention.
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
     * Scenariusz: porównanie puli wątków dla CPU-bound i IO-bound workload.
     *
     * Co obserwować:
     *
     * CPU-bound:
     * - CPU saturation,
     * - context switching,
     * - brak zysku z nadmiernej liczby wątków.
     *
     * IO-bound:
     * - wiele wątków czeka,
     * - większa pula może poprawić throughput do pewnego momentu,
     * - potem pojawia się narzut przełączania kontekstu.
     */
    public ThreadPoolExperimentResponse threadPoolExperiment(int threads, int tasks, String workloadType) {
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Callable<Long>> callables = new ArrayList<>();

        for (int i = 0; i < tasks; i++) {
            callables.add(() -> {
                if ("IO".equalsIgnoreCase(workloadType)) {
                    /*
                     * Symulacja IO-bound przez sleep.
                     *
                     * Wątek nie zużywa CPU, tylko czeka.
                     */
                    Thread.sleep(25);
                    return 1L;
                }

                /*
                 * Domyślnie traktujemy workload jako CPU-bound.
                 */
                return cpuWork();
            });
        }

        long start = System.nanoTime();

        try {
            /*
             * invokeAll blokuje do zakończenia wszystkich zadań.
             */
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

    /**
     * Scenariusz: alokacje BigDecimal.
     *
     * BigDecimal jest potrzebny w wielu zastosowaniach finansowych, ale jest
     * obiektem i często tworzy obiekty pośrednie.
     *
     * Co obserwować:
     *
     * - allocation rate,
     * - koszt tworzenia BigDecimal.valueOf(...),
     * - koszt movePointLeft(...),
     * - koszt add(...),
     * - porównanie z long reprezentującym grosze w JMH.
     */
    public ProfilingRunResponse numericAllocationScenario(int iterations) {
        long start = System.nanoTime();

        BigDecimal sum = BigDecimal.ZERO;

        for (int i = 0; i < iterations; i++) {
            sum = sum.add(BigDecimal.valueOf(i).movePointLeft(2));
        }

        /*
         * Prosta ochrona przed tym, żeby JIT nie uznał całego wyniku za martwy kod.
         *
         * To nadal nie robi z tej metody poprawnego benchmarku.
         * Do benchmarków używaj JMH.
         */
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

    /**
     * Sztuczna praca CPU-bound.
     *
     * Nie wykonuje IO, nie śpi, tylko zużywa CPU.
     *
     * Przy tym scenariuszu można obserwować, że zwiększanie liczby wątków ponad
     * liczbę rdzeni zwykle nie poprawia wydajności, a czasem ją pogarsza.
     */
    private long cpuWork() {
        long x = 0;

        for (int i = 0; i < 75_000; i++) {
            x += (i * 31L) ^ (x >>> 3);
        }

        return x;
    }

    /**
     * Czeka na CountDownLatch i poprawnie obsługuje InterruptedException.
     *
     * Po złapaniu InterruptedException przywracamy flagę przerwania:
     *
     * Thread.currentThread().interrupt();
     *
     * To jest ważna praktyka w kodzie współbieżnym.
     */
    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for profiling scenario", ex);
        }
    }

    /**
     * Czeka na zakończenie wszystkich zadań.
     *
     * future.get(30, TimeUnit.SECONDS) zapobiega wiszeniu testu/scenariusza
     * bez końca, jeśli któreś zadanie utknie.
     */
    private void waitFor(List<Future<?>> futures) {
        for (Future<?> future : futures) {
            try {
                future.get(30, TimeUnit.SECONDS);
            } catch (Exception ex) {
                throw new IllegalStateException("Profiling task failed", ex);
            }
        }
    }

    /**
     * Poprawnie zamyka ExecutorService.
     *
     * Najpierw próbujemy graceful shutdown.
     * Jeśli zadania nie zakończą się w czasie, wymuszamy shutdownNow().
     *
     * Przy InterruptedException:
     * - wywołujemy shutdownNow(),
     * - przywracamy flagę przerwania.
     */
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