package pl.jakubtworek.booking.service.pitfall;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Bean edukacyjny pokazujący lifecycle obiektu zarządzanego przez Springa.
 *
 * Ten komponent nie realizuje logiki biznesowej.
 * Służy do obserwacji tego, kiedy Spring wywołuje konkretne callbacki cyklu życia:
 *
 * - konstrukcja obiektu,
 * - @PostConstruct,
 * - InitializingBean.afterPropertiesSet(),
 * - @PreDestroy.
 *
 * Domyślnie bean oznaczony @Component ma scope singleton, więc Spring tworzy
 * jedną instancję tego obiektu na ApplicationContext.
 */
@Component
public class BeanLifecycleProbe implements InitializingBean {

    /**
     * Losowy identyfikator instancji.
     *
     * Dzięki niemu można łatwo sprawdzić, czy dwa pobrania beana z kontenera
     * zwracają tę samą instancję.
     *
     * Dla domyślnego singleton scope instanceId powinien być taki sam
     * przy każdym odczycie.
     */
    private final UUID instanceId = UUID.randomUUID();

    /**
     * Flaga informująca, czy metoda oznaczona @PostConstruct została wywołana.
     *
     * AtomicBoolean jest użyty defensywnie, bo bean singleton może być odczytywany
     * przez wiele requestów/wątków.
     */
    private final AtomicBoolean postConstructCalled = new AtomicBoolean(false);

    /**
     * Flaga informująca, czy afterPropertiesSet() zostało wywołane.
     *
     * Ta metoda pochodzi z interfejsu InitializingBean.
     */
    private final AtomicBoolean afterPropertiesSetCalled = new AtomicBoolean(false);

    /**
     * Flaga informująca, czy @PreDestroy zostało wywołane.
     *
     * Podczas normalnego działania aplikacji zwykle będzie false.
     * Zmieni się na true dopiero przy zamykaniu ApplicationContext.
     */
    private final AtomicBoolean preDestroyCalled = new AtomicBoolean(false);

    /**
     * Callback wywoływany przez Springa po utworzeniu beana i wstrzyknięciu zależności.
     *
     * @PostConstruct jest wygodnym miejscem na prostą inicjalizację,
     * ale nie powinno się tu wykonywać ciężkich operacji blokujących start aplikacji,
     * jeśli nie jest to konieczne.
     */
    @PostConstruct
    void postConstruct() {
        postConstructCalled.set(true);
    }

    /**
     * Callback z interfejsu InitializingBean.
     *
     * Spring wywołuje tę metodę po ustawieniu właściwości beana.
     *
     * W praktyce częściej używa się @PostConstruct, bo nie wiąże klasy bezpośrednio
     * z interfejsem Springa. Tutaj InitializingBean jest użyty celowo edukacyjnie.
     */
    @Override
    public void afterPropertiesSet() {
        afterPropertiesSetCalled.set(true);
    }

    /**
     * Callback wywoływany przy niszczeniu beana.
     *
     * Dla singletonów dzieje się to przy zamykaniu kontekstu aplikacji.
     *
     * @PreDestroy jest dobrym miejscem na sprzątanie zasobów:
     * - zamknięcie połączeń,
     * - zatrzymanie executorów,
     * - flush buforów,
     * - zwolnienie zasobów systemowych.
     */
    @PreDestroy
    void preDestroy() {
        preDestroyCalled.set(true);
    }

    /**
     * Zwraca identyfikator instancji.
     *
     * Używane przez endpoint edukacyjny do pokazania, czy bean jest singletonem.
     */
    public UUID getInstanceId() {
        return instanceId;
    }

    /**
     * Informuje, czy @PostConstruct zostało wykonane.
     */
    public boolean isPostConstructCalled() {
        return postConstructCalled.get();
    }

    /**
     * Informuje, czy InitializingBean.afterPropertiesSet() zostało wykonane.
     */
    public boolean isAfterPropertiesSetCalled() {
        return afterPropertiesSetCalled.get();
    }

    /**
     * Informuje, czy @PreDestroy zostało wykonane.
     *
     * W zwykłym requestcie HTTP najczęściej będzie false, bo aplikacja nadal działa.
     */
    public boolean isPreDestroyCalled() {
        return preDestroyCalled.get();
    }
}