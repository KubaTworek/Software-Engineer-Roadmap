package pl.jakubtworek.booking.controller.profiling;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.jakubtworek.booking.dto.profiling.AllocationPressureResponse;
import pl.jakubtworek.booking.dto.profiling.LockContentionResponse;
import pl.jakubtworek.booking.dto.profiling.OrganizationReportProfilingResponse;
import pl.jakubtworek.booking.dto.profiling.ProfilingReservationResponse;
import pl.jakubtworek.booking.dto.profiling.ProfilingRunResponse;
import pl.jakubtworek.booking.dto.profiling.ThreadPoolExperimentResponse;
import pl.jakubtworek.booking.service.profiling.JvmProfilingService;

import java.util.UUID;

/**
 * Kontroler REST z endpointami do eksperymentów JVM/profiling.
 *
 * To nie jest kontroler biznesowy.
 * Jego zadaniem jest uruchamianie kontrolowanych scenariuszy obciążeniowych,
 * które pomagają obserwować:
 *
 * - CPU hotspoty,
 * - allocation rate,
 * - GC pauses,
 * - lock contention,
 * - wpływ liczby wątków,
 * - różnicę między CPU-bound i IO-bound workload,
 * - koszt BigDecimal i alokacji obiektów.
 *
 * Endpointy z tej klasy powinny być traktowane jako narzędzia edukacyjne.
 * W realnej aplikacji nie powinny być publicznie dostępne.
 */
@RestController
@RequestMapping("/api/profiling")
public class JvmProfilingController {

    /**
     * Serwis wykonujący właściwe scenariusze profilujące.
     *
     * Kontroler tylko przyjmuje parametry HTTP i deleguje pracę dalej.
     * Logika generowania obciążenia nie powinna znajdować się w kontrolerze.
     */
    private final JvmProfilingService jvmProfilingService;

    /**
     * Constructor injection.
     *
     * Dzięki temu zależność jest jawna, a kontroler jest prosty do testowania.
     */
    public JvmProfilingController(JvmProfilingService jvmProfilingService) {
        this.jvmProfilingService = jvmProfilingService;
    }

    /**
     * Uruchamia serię krótkich rezerwacji sekwencyjnie albo w prostym burst mode,
     * zależnie od implementacji serwisu.
     *
     * Endpoint:
     *
     * POST /api/profiling/events/{eventId}/short-reservations?requests=100
     *
     * Ten scenariusz służy do obserwacji:
     *
     * - kosztu krótkich operacji biznesowych,
     * - liczby zapytań SQL,
     * - alokacji DTO/encji,
     * - wpływu transakcji,
     * - latency pojedynczego flow rezerwacji.
     */
    @PostMapping("/events/{eventId}/short-reservations")
    public ProfilingReservationResponse shortReservations(
            @PathVariable UUID eventId,
            @RequestParam(defaultValue = "100") int requests
    ) {
        return jvmProfilingService.runShortReservationBurst(eventId, requests);
    }

    /**
     * Uruchamia wiele równoległych prób rezerwacji.
     *
     * Endpoint:
     *
     * POST /api/profiling/events/{eventId}/parallel-reservations?requests=100&threads=32
     *
     * Ten scenariusz jest przydatny do obserwacji:
     *
     * - contention na bazie danych,
     * - zachowania atomowego update'u dostępności,
     * - wpływu liczby wątków na throughput,
     * - connection pool pressure,
     * - latency pod równoległym obciążeniem.
     *
     * Uwaga:
     * zbyt duża liczba threads może bardziej zaszkodzić niż pomóc.
     * Więcej wątków nie oznacza automatycznie większej wydajności.
     */
    @PostMapping("/events/{eventId}/parallel-reservations")
    public ProfilingReservationResponse parallelReservations(
            @PathVariable UUID eventId,
            @RequestParam(defaultValue = "100") int requests,
            @RequestParam(defaultValue = "32") int threads
    ) {
        return jvmProfilingService.runParallelReservationBurst(eventId, requests, threads);
    }

    /**
     * Generuje raport dla organizacji.
     *
     * Endpoint:
     *
     * GET /api/profiling/organizations/{organizationId}/report
     *
     * Ten scenariusz pomaga obserwować:
     *
     * - koszt agregacji,
     * - liczbę zapytań SQL,
     * - potencjalne N+1,
     * - CPU zużyte na budowanie raportu,
     * - alokacje kolekcji i DTO.
     */
    @GetMapping("/organizations/{organizationId}/report")
    public OrganizationReportProfilingResponse organizationReport(@PathVariable UUID organizationId) {
        return jvmProfilingService.organizationReport(organizationId);
    }

    /**
     * Generuje presję alokacyjną przez utworzenie dużej liczby obiektów.
     *
     * Endpoint:
     *
     * GET /api/profiling/allocations?objects=100000
     *
     * Ten scenariusz służy do obserwacji:
     *
     * - allocation rate,
     * - pracy GC,
     * - krótkotrwałych obiektów,
     * - wpływu liczby alokacji na latency.
     *
     * W JFR/Profilerze warto patrzeć na allocation hotspots.
     */
    @GetMapping("/allocations")
    public AllocationPressureResponse allocations(@RequestParam(defaultValue = "100000") int objects) {
        return jvmProfilingService.allocationPressure(objects);
    }

    /**
     * Generuje lock contention na wspólnym zasobie.
     *
     * Endpoint:
     *
     * GET /api/profiling/lock-contention?threads=32&incrementsPerThread=100000
     *
     * Ten scenariusz pomaga zobaczyć:
     *
     * - wątki blokujące się na tym samym locku,
     * - spadek throughputu przez synchronized,
     * - monitor contention w profilerze,
     * - koszt sekcji krytycznej przy dużej liczbie wątków.
     */
    @GetMapping("/lock-contention")
    public LockContentionResponse lockContention(
            @RequestParam(defaultValue = "32") int threads,
            @RequestParam(defaultValue = "100000") int incrementsPerThread
    ) {
        return jvmProfilingService.lockContention(threads, incrementsPerThread);
    }

    /**
     * Eksperymentuje z różnym rozmiarem puli wątków i typem workloadu.
     *
     * Endpoint:
     *
     * GET /api/profiling/thread-pool?threads=8&tasks=200&workloadType=CPU
     *
     * workloadType może oznaczać np.:
     *
     * - CPU — zadania CPU-bound,
     * - IO — zadania symulujące oczekiwanie na IO.
     *
     * Ten endpoint pokazuje, że dobór liczby wątków zależy od typu pracy:
     *
     * - dla CPU-bound zwykle nie ma sensu mieć dużo więcej aktywnych wątków
     *   niż rdzeni CPU,
     * - dla IO-bound większa liczba wątków może pomóc, bo wiele z nich czeka.
     */
    @GetMapping("/thread-pool")
    public ThreadPoolExperimentResponse threadPool(
            @RequestParam(defaultValue = "8") int threads,
            @RequestParam(defaultValue = "200") int tasks,
            @RequestParam(defaultValue = "CPU") String workloadType
    ) {
        return jvmProfilingService.threadPoolExperiment(threads, tasks, workloadType);
    }

    /**
     * Generuje scenariusz alokacyjny z BigDecimal.
     *
     * Endpoint:
     *
     * GET /api/profiling/big-decimal-allocation?iterations=100000
     *
     * Ten scenariusz pomaga zobaczyć:
     *
     * - koszt BigDecimal,
     * - liczbę obiektów pośrednich,
     * - allocation pressure,
     * - różnicę między precyzją a wydajnością.
     *
     * W kodzie finansowym BigDecimal często jest potrzebny, ale warto rozumieć,
     * że nie jest darmowy. Czasem dla kwot pieniężnych sensowny jest long
     * reprezentujący najmniejszą jednostkę, np. grosze.
     */
    @GetMapping("/big-decimal-allocation")
    public ProfilingRunResponse bigDecimalAllocation(@RequestParam(defaultValue = "100000") int iterations) {
        return jvmProfilingService.numericAllocationScenario(iterations);
    }
}