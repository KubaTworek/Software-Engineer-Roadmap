package pl.jakubtworek.booking.service.pitfall;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import pl.jakubtworek.booking.aop.Measured;

/**
 * Serwis edukacyjny pokazujący działanie Spring AOP i proxy boundary.
 *
 * Główna idea:
 *
 * - metoda measuredOperation(...) jest oznaczona własną adnotacją @Measured,
 * - aspekt powinien przechwycić wywołanie tej metody,
 * - ale stanie się to tylko wtedy, gdy wywołanie przejdzie przez proxy Springa.
 *
 * Jeśli metoda zostanie wywołana przez this, aspekt zostanie ominięty.
 *
 * To jest dokładnie ten sam mechanizm, który odpowiada za wiele pułapek z:
 *
 * - @Transactional,
 * - @Cacheable,
 * - @Async,
 * - @PreAuthorize,
 * - własnymi adnotacjami AOP.
 */
@Service
public class MeasuredPitfallService {

    /**
     * ObjectProvider pozwala leniwie pobrać beana zarządzanego przez Springa.
     *
     * W tym przypadku chodzi o pobranie proxy tej samej klasy.
     *
     * Bez tego, gdybyśmy wywołali:
     *
     * this.measuredOperation(input)
     *
     * wywołanie ominęłoby proxy i aspekt @Measured by się nie wykonał.
     */
    private final ObjectProvider<MeasuredPitfallService> selfProvider;

    /**
     * Constructor injection.
     *
     * Wstrzykujemy ObjectProvider, a nie bezpośrednio MeasuredPitfallService,
     * żeby uniknąć problemów z cykliczną zależnością podczas tworzenia beana.
     */
    public MeasuredPitfallService(ObjectProvider<MeasuredPitfallService> selfProvider) {
        this.selfProvider = selfProvider;
    }

    /**
     * Celowo błędny wariant: wywołanie metody mierzonej przez this.
     *
     * Flow:
     *
     * callMeasuredMethodThroughThis(...)
     *   -> this.measuredOperation(...)
     *
     * To wywołanie nie przechodzi przez proxy Springa.
     *
     * Efekt:
     * aspekt obsługujący @Measured nie zostanie uruchomiony.
     *
     * W endpointzie edukacyjnym powinno to dać measurementCount = 0.
     */
    public String callMeasuredMethodThroughThis(String input) {
        return measuredOperation(input);
    }

    /**
     * Poprawny wariant: wywołanie metody mierzonej przez proxy.
     *
     * Flow:
     *
     * callMeasuredMethodThroughProxy(...)
     *   -> selfProvider.getObject()
     *   -> proxy Springa
     *   -> measuredOperation(...)
     *   -> aspekt @Measured
     *
     * Dzięki temu aspekt ma szansę przechwycić wywołanie i zapisać pomiar.
     */
    public String callMeasuredMethodThroughProxy(String input) {
        return selfProvider.getObject().measuredOperation(input);
    }

    /**
     * Metoda oznaczona własną adnotacją @Measured.
     *
     * Aspekt powinien zmierzyć czas wykonania tej metody i zapisać wynik
     * pod nazwą:
     *
     * spring-pitfall-measured-operation
     *
     * Sama logika metody jest banalna, bo nie chodzi tutaj o biznes,
     * tylko o pokazanie, czy aspekt się wykonał.
     */
    @Measured("spring-pitfall-measured-operation")
    public String measuredOperation(String input) {
        return input.toUpperCase();
    }
}