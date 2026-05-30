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

@RestController
@RequestMapping("/api/profiling")
public class JvmProfilingController {
    private final JvmProfilingService jvmProfilingService;

    public JvmProfilingController(JvmProfilingService jvmProfilingService) {
        this.jvmProfilingService = jvmProfilingService;
    }

    @PostMapping("/events/{eventId}/short-reservations")
    public ProfilingReservationResponse shortReservations(
            @PathVariable UUID eventId,
            @RequestParam(defaultValue = "100") int requests
    ) {
        return jvmProfilingService.runShortReservationBurst(eventId, requests);
    }

    @PostMapping("/events/{eventId}/parallel-reservations")
    public ProfilingReservationResponse parallelReservations(
            @PathVariable UUID eventId,
            @RequestParam(defaultValue = "100") int requests,
            @RequestParam(defaultValue = "32") int threads
    ) {
        return jvmProfilingService.runParallelReservationBurst(eventId, requests, threads);
    }

    @GetMapping("/organizations/{organizationId}/report")
    public OrganizationReportProfilingResponse organizationReport(@PathVariable UUID organizationId) {
        return jvmProfilingService.organizationReport(organizationId);
    }

    @GetMapping("/allocations")
    public AllocationPressureResponse allocations(@RequestParam(defaultValue = "100000") int objects) {
        return jvmProfilingService.allocationPressure(objects);
    }

    @GetMapping("/lock-contention")
    public LockContentionResponse lockContention(
            @RequestParam(defaultValue = "32") int threads,
            @RequestParam(defaultValue = "100000") int incrementsPerThread
    ) {
        return jvmProfilingService.lockContention(threads, incrementsPerThread);
    }

    @GetMapping("/thread-pool")
    public ThreadPoolExperimentResponse threadPool(
            @RequestParam(defaultValue = "8") int threads,
            @RequestParam(defaultValue = "200") int tasks,
            @RequestParam(defaultValue = "CPU") String workloadType
    ) {
        return jvmProfilingService.threadPoolExperiment(threads, tasks, workloadType);
    }

    @GetMapping("/big-decimal-allocation")
    public ProfilingRunResponse bigDecimalAllocation(@RequestParam(defaultValue = "100000") int iterations) {
        return jvmProfilingService.numericAllocationScenario(iterations);
    }
}
