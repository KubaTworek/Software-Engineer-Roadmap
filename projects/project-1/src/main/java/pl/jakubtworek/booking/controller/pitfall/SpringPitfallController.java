package pl.jakubtworek.booking.controller.pitfall;

import org.springframework.web.bind.annotation.*;
import pl.jakubtworek.booking.aop.MeasuredCall;
import pl.jakubtworek.booking.aop.MeasurementRegistry;
import pl.jakubtworek.booking.dto.SpringPitfallReservationView;
import pl.jakubtworek.booking.entity.Reservation;
import pl.jakubtworek.booking.service.pitfall.BeanLifecyclePitfallService;
import pl.jakubtworek.booking.service.pitfall.BeanLifecycleView;
import pl.jakubtworek.booking.service.pitfall.LazyLoadingPitfallService;
import pl.jakubtworek.booking.service.pitfall.MeasuredPitfallService;
import pl.jakubtworek.booking.service.pitfall.SelfInvocationPitfallService;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/spring-pitfalls")
public class SpringPitfallController {
    private final SelfInvocationPitfallService selfInvocationPitfallService;
    private final LazyLoadingPitfallService lazyLoadingPitfallService;
    private final MeasuredPitfallService measuredPitfallService;
    private final MeasurementRegistry measurementRegistry;
    private final BeanLifecyclePitfallService beanLifecyclePitfallService;

    public SpringPitfallController(
            SelfInvocationPitfallService selfInvocationPitfallService,
            LazyLoadingPitfallService lazyLoadingPitfallService,
            MeasuredPitfallService measuredPitfallService,
            MeasurementRegistry measurementRegistry,
            BeanLifecyclePitfallService beanLifecyclePitfallService
    ) {
        this.selfInvocationPitfallService = selfInvocationPitfallService;
        this.lazyLoadingPitfallService = lazyLoadingPitfallService;
        this.measuredPitfallService = measuredPitfallService;
        this.measurementRegistry = measurementRegistry;
        this.beanLifecyclePitfallService = beanLifecyclePitfallService;
    }

    @GetMapping("/transactional/self-invocation")
    public Map<String, Boolean> selfInvocation() {
        return Map.of(
                "transactionActiveWhenCalledThroughThis", selfInvocationPitfallService.callTransactionalMethodThroughThis(),
                "transactionActiveWhenCalledThroughProxy", selfInvocationPitfallService.callTransactionalMethodThroughProxy()
        );
    }

    @GetMapping("/reservations/{reservationId}/lazy-broken")
    public SpringPitfallReservationView lazyBroken(@PathVariable UUID reservationId) {
        Reservation reservation = lazyLoadingPitfallService.loadDetachedReservationWithLazyRelations(reservationId);
        return lazyLoadingPitfallService.toView(reservation);
    }

    @GetMapping("/reservations/{reservationId}/lazy-fixed-transaction")
    public SpringPitfallReservationView lazyFixedByTransaction(@PathVariable UUID reservationId) {
        return lazyLoadingPitfallService.mapInsideTransaction(reservationId);
    }

    @GetMapping("/reservations/{reservationId}/lazy-fixed-projection")
    public SpringPitfallReservationView lazyFixedByProjection(@PathVariable UUID reservationId) {
        return lazyLoadingPitfallService.loadUsingDtoProjection(reservationId);
    }

    @GetMapping("/reservations/{reservationId}/lazy-fixed-fetch-join")
    public SpringPitfallReservationView lazyFixedByFetchJoin(@PathVariable UUID reservationId) {
        return lazyLoadingPitfallService.loadUsingFetchJoin(reservationId);
    }

    @GetMapping("/reservations/{reservationId}/lazy-fixed-entity-graph")
    public SpringPitfallReservationView lazyFixedByEntityGraph(@PathVariable UUID reservationId) {
        return lazyLoadingPitfallService.loadUsingEntityGraph(reservationId);
    }

    @PostMapping("/aop/through-this")
    public Map<String, Object> measuredThroughThis(@RequestParam(defaultValue = "spring") String input) {
        measurementRegistry.clear();
        String result = measuredPitfallService.callMeasuredMethodThroughThis(input);
        List<MeasuredCall> measurements = measurementRegistry.findAll();
        return Map.of("result", result, "measurements", measurements, "measurementCount", measurements.size());
    }

    @PostMapping("/aop/through-proxy")
    public Map<String, Object> measuredThroughProxy(@RequestParam(defaultValue = "spring") String input) {
        measurementRegistry.clear();
        String result = measuredPitfallService.callMeasuredMethodThroughProxy(input);
        List<MeasuredCall> measurements = measurementRegistry.findAll();
        return Map.of("result", result, "measurements", measurements, "measurementCount", measurements.size());
    }

    @GetMapping("/bean-lifecycle")
    public BeanLifecycleView beanLifecycle() {
        return beanLifecyclePitfallService.inspectSingletonBean();
    }
}
