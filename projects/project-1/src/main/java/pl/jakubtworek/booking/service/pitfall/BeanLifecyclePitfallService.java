package pl.jakubtworek.booking.service.pitfall;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * Serwis edukacyjny pokazujący lifecycle i scope beana Springa.
 *
 * Ta klasa nie realizuje logiki biznesowej.
 * Jej celem jest pokazanie kilku faktów o kontenerze Springa:
 *
 * - domyślny scope beana to singleton,
 * - ten sam bean pobrany wiele razy z kontenera zwykle jest tą samą instancją,
 * - Spring wywołuje lifecycle callbacki, np. @PostConstruct,
 * - Spring może wywołać afterPropertiesSet(), jeśli bean implementuje InitializingBean,
 * - @PreDestroy zostanie wywołane dopiero przy zamykaniu kontekstu aplikacji.
 */
@Service
public class BeanLifecyclePitfallService {

    /**
     * ObjectProvider pozwala pobrać BeanLifecycleProbe z kontenera Springa.
     *
     * Używamy go tutaj edukacyjnie, żeby dwa razy poprosić kontener o ten sam bean
     * i sprawdzić, czy dostajemy tę samą instancję.
     *
     * Przy domyślnym scope singleton:
     *
     * probeProvider.getObject()
     * probeProvider.getObject()
     *
     * powinny zwrócić dokładnie ten sam obiekt.
     */
    private final ObjectProvider<BeanLifecycleProbe> probeProvider;

    /**
     * Constructor injection.
     *
     * Wstrzykujemy ObjectProvider, a nie bezpośrednio BeanLifecycleProbe,
     * żeby móc jawnie pokazać moment pobrania beana z kontenera.
     */
    public BeanLifecyclePitfallService(ObjectProvider<BeanLifecycleProbe> probeProvider) {
        this.probeProvider = probeProvider;
    }

    /**
     * Sprawdza zachowanie beana BeanLifecycleProbe.
     *
     * Flow:
     *
     * 1. Pobierz BeanLifecycleProbe z kontenera Springa.
     * 2. Pobierz go drugi raz.
     * 3. Porównaj referencje first == second.
     * 4. Zwróć informacje o lifecycle callbackach.
     *
     * Przy domyślnym singleton scope oczekujemy:
     *
     * - firstInstanceId == secondInstanceId,
     * - sameInstance == true,
     * - postConstructCalled == true,
     * - afterPropertiesSetCalled == true,
     * - preDestroyCalled == false podczas działania aplikacji.
     *
     * preDestroyCalled będzie false, bo @PreDestroy wykona się dopiero przy
     * zamykaniu ApplicationContext, a nie podczas zwykłego requestu HTTP.
     */
    public BeanLifecycleView inspectSingletonBean() {
        BeanLifecycleProbe first = probeProvider.getObject();
        BeanLifecycleProbe second = probeProvider.getObject();

        return new BeanLifecycleView(
                first.getInstanceId(),
                second.getInstanceId(),
                first == second,
                first.isPostConstructCalled(),
                first.isAfterPropertiesSetCalled(),
                first.isPreDestroyCalled()
        );
    }
}