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

/**
 * Kontroler edukacyjny pokazujący typowe pułapki Springa.
 *
 * To nie jest kontroler biznesowy.
 *
 * Jego celem jest świadome pokazanie sytuacji, w których Spring "nie działa",
 * jeśli źle rozumiemy:
 *
 * - proxy,
 * - @Transactional,
 * - lazy loading JPA,
 * - AOP,
 * - lifecycle beanów,
 * - singleton scope.
 *
 * Endpointy z tej klasy powinny być traktowane jako materiał edukacyjny.
 * W realnej aplikacji nie powinny być publiczne.
 */
@RestController
@RequestMapping("/api/spring-pitfalls")
public class SpringPitfallController {

    /**
     * Serwis pokazujący problem self-invocation z @Transactional.
     *
     * Główna idea:
     * jeśli metoda oznaczona @Transactional zostanie wywołana przez this,
     * to wywołanie omija proxy Springa i transakcja może się nie uruchomić.
     */
    private final SelfInvocationPitfallService selfInvocationPitfallService;

    /**
     * Serwis pokazujący problem LazyInitializationException.
     *
     * Używany do pokazania błędnego wariantu oraz kilku poprawnych rozwiązań:
     * - mapowanie wewnątrz transakcji,
     * - DTO projection,
     * - fetch join,
     * - EntityGraph.
     */
    private final LazyLoadingPitfallService lazyLoadingPitfallService;

    /**
     * Serwis pokazujący działanie Spring AOP i proxy boundary.
     *
     * Używany do porównania:
     * - wywołania metody mierzonej przez this,
     * - wywołania tej samej metody przez proxy.
     */
    private final MeasuredPitfallService measuredPitfallService;

    /**
     * Prosty rejestr pomiarów zapisanych przez aspekt @Measured.
     *
     * Dzięki niemu endpoint może pokazać, czy aspekt faktycznie się wykonał.
     */
    private final MeasurementRegistry measurementRegistry;

    /**
     * Serwis pokazujący podstawowe informacje o lifecycle beana.
     *
     * Przydaje się do obserwacji singleton scope, inicjalizacji i stanu beana.
     */
    private final BeanLifecyclePitfallService beanLifecyclePitfallService;

    /**
     * Constructor injection.
     *
     * Zależności są jawne, pola mogą być final, a kontroler nie musi używać
     * field injection.
     */
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

    /**
     * Pokazuje różnicę między wywołaniem metody transakcyjnej przez this
     * a wywołaniem przez proxy Springa.
     *
     * Endpoint:
     *
     * GET /api/spring-pitfalls/transactional/self-invocation
     *
     * Oczekiwany sens wyniku:
     *
     * - transactionActiveWhenCalledThroughThis = false,
     * - transactionActiveWhenCalledThroughProxy = true.
     *
     * To pokazuje, że @Transactional jest realizowane przez proxy.
     * Jeśli omijasz proxy, omijasz też logikę transakcyjną.
     */
    @GetMapping("/transactional/self-invocation")
    public Map<String, Boolean> selfInvocation() {
        return Map.of(
                "transactionActiveWhenCalledThroughThis", selfInvocationPitfallService.callTransactionalMethodThroughThis(),
                "transactionActiveWhenCalledThroughProxy", selfInvocationPitfallService.callTransactionalMethodThroughProxy()
        );
    }

    /**
     * Celowo błędny endpoint pokazujący LazyInitializationException.
     *
     * Endpoint:
     *
     * GET /api/spring-pitfalls/reservations/{reservationId}/lazy-broken
     *
     * Flow:
     *
     * 1. Serwis ładuje Reservation.
     * 2. Transakcja/persistence context się kończy.
     * 3. Kontroler próbuje zmapować lazy relacje.
     * 4. Hibernate nie może ich już dociągnąć.
     *
     * Ten endpoint powinien pokazać, dlaczego nie warto wynosić encji JPA
     * poza bezpieczną granicę transakcji, jeśli później dotykasz lazy relations.
     */
    @GetMapping("/reservations/{reservationId}/lazy-broken")
    public SpringPitfallReservationView lazyBroken(@PathVariable UUID reservationId) {
        Reservation reservation = lazyLoadingPitfallService.loadDetachedReservationWithLazyRelations(reservationId);
        return lazyLoadingPitfallService.toView(reservation);
    }

    /**
     * Poprawia lazy loading przez mapowanie DTO wewnątrz transakcji.
     *
     * Endpoint:
     *
     * GET /api/spring-pitfalls/reservations/{reservationId}/lazy-fixed-transaction
     *
     * Tutaj serwis pobiera encję i od razu mapuje ją do DTO, zanim persistence
     * context zostanie zamknięty.
     */
    @GetMapping("/reservations/{reservationId}/lazy-fixed-transaction")
    public SpringPitfallReservationView lazyFixedByTransaction(@PathVariable UUID reservationId) {
        return lazyLoadingPitfallService.mapInsideTransaction(reservationId);
    }

    /**
     * Poprawia lazy loading przez DTO projection.
     *
     * Endpoint:
     *
     * GET /api/spring-pitfalls/reservations/{reservationId}/lazy-fixed-projection
     *
     * Projection pobiera od razu dokładnie te pola, których potrzebuje odpowiedź.
     * Nie trzeba materializować pełnej encji ani dotykać lazy relations.
     */
    @GetMapping("/reservations/{reservationId}/lazy-fixed-projection")
    public SpringPitfallReservationView lazyFixedByProjection(@PathVariable UUID reservationId) {
        return lazyLoadingPitfallService.loadUsingDtoProjection(reservationId);
    }

    /**
     * Poprawia lazy loading przez fetch join.
     *
     * Endpoint:
     *
     * GET /api/spring-pitfalls/reservations/{reservationId}/lazy-fixed-fetch-join
     *
     * Fetch join jawnie pobiera Reservation razem z wymaganymi relacjami,
     * np. event i customer.
     */
    @GetMapping("/reservations/{reservationId}/lazy-fixed-fetch-join")
    public SpringPitfallReservationView lazyFixedByFetchJoin(@PathVariable UUID reservationId) {
        return lazyLoadingPitfallService.loadUsingFetchJoin(reservationId);
    }

    /**
     * Poprawia lazy loading przez EntityGraph.
     *
     * Endpoint:
     *
     * GET /api/spring-pitfalls/reservations/{reservationId}/lazy-fixed-entity-graph
     *
     * EntityGraph pozwala wskazać, które relacje Hibernate ma dociągnąć dla
     * konkretnej metody repozytorium.
     */
    @GetMapping("/reservations/{reservationId}/lazy-fixed-entity-graph")
    public SpringPitfallReservationView lazyFixedByEntityGraph(@PathVariable UUID reservationId) {
        return lazyLoadingPitfallService.loadUsingEntityGraph(reservationId);
    }

    /**
     * Pokazuje, że aspekt @Measured może nie zadziałać przy wywołaniu przez this.
     *
     * Endpoint:
     *
     * POST /api/spring-pitfalls/aop/through-this?input=spring
     *
     * Flow:
     *
     * 1. Czyścimy rejestr pomiarów.
     * 2. Wywołujemy metodę oznaczoną @Measured przez this.
     * 3. Sprawdzamy, ile pomiarów zapisał aspekt.
     *
     * Oczekiwany efekt edukacyjny:
     * measurementCount może być 0, bo wywołanie ominęło proxy.
     */
    @PostMapping("/aop/through-this")
    public Map<String, Object> measuredThroughThis(@RequestParam(defaultValue = "spring") String input) {
        measurementRegistry.clear();

        String result = measuredPitfallService.callMeasuredMethodThroughThis(input);

        List<MeasuredCall> measurements = measurementRegistry.findAll();

        return Map.of(
                "result", result,
                "measurements", measurements,
                "measurementCount", measurements.size()
        );
    }

    /**
     * Pokazuje, że aspekt @Measured działa, gdy metoda jest wywołana przez proxy.
     *
     * Endpoint:
     *
     * POST /api/spring-pitfalls/aop/through-proxy?input=spring
     *
     * Oczekiwany efekt:
     * measurementCount powinien być większy od 0, bo wywołanie przeszło przez proxy.
     */
    @PostMapping("/aop/through-proxy")
    public Map<String, Object> measuredThroughProxy(@RequestParam(defaultValue = "spring") String input) {
        measurementRegistry.clear();

        String result = measuredPitfallService.callMeasuredMethodThroughProxy(input);

        List<MeasuredCall> measurements = measurementRegistry.findAll();

        return Map.of(
                "result", result,
                "measurements", measurements,
                "measurementCount", measurements.size()
        );
    }

    /**
     * Pokazuje podstawowe informacje o lifecycle beana.
     *
     * Endpoint:
     *
     * GET /api/spring-pitfalls/bean-lifecycle
     *
     * Można tu pokazać m.in.:
     *
     * - czy bean jest singletonem,
     * - kiedy został utworzony,
     * - czy @PostConstruct się wykonał,
     * - czy stan beana jest współdzielony między requestami.
     */
    @GetMapping("/bean-lifecycle")
    public BeanLifecycleView beanLifecycle() {
        return beanLifecyclePitfallService.inspectSingletonBean();
    }
}