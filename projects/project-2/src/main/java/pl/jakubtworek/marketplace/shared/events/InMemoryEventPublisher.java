package pl.jakubtworek.marketplace.shared.events;

import pl.jakubtworek.marketplace.shared.kernel.DomainEvent;

import java.util.List;

/**
 * Przestarzała, zachowana dla kompatybilności nazwa publishera in-memory.
 *
 * Ta klasa istnieje głównie po to, żeby starsze testy, przykłady albo wcześniejsze fazy
 * projektu nadal się kompilowały.
 *
 * W aktualnym kodzie preferowaną nazwą i implementacją jest ApplicationEventBus.
 *
 * Ważne:
 * - to nadal jest synchroniczny mechanizm in-memory,
 * - nie zapisuje eventów do outboxa,
 * - nie publikuje eventów do Kafki,
 * - nie zapewnia retry, DLQ ani trwałości,
 * - działa tylko w ramach jednego procesu JVM.
 *
 * Po fazie outbox/Kafka ta klasa nie powinna być używana jako główny mechanizm publikacji
 * zdarzeń w aplikacji produkcyjnej.
 */
@Deprecated
public class InMemoryEventPublisher implements EventPublisher {

    /**
     * Właściwa implementacja event busa, do której delegujemy publikację.
     *
     * Dzięki temu nie utrzymujemy dwóch niezależnych implementacji dispatchowania eventów.
     * InMemoryEventPublisher jest tylko cienką warstwą kompatybilności.
     */
    private final ApplicationEventBus delegate;

    /**
     * Tworzy publisher in-memory na podstawie listy handlerów.
     *
     * Konstruktor przyjmuje te same handlery, które były używane we wcześniejszych testach
     * i przykładach. Wewnątrz tworzy ApplicationEventBus i przekazuje mu listę handlerów.
     */
    public InMemoryEventPublisher(List<DomainEventHandler<?>> handlers) {
        this.delegate = new ApplicationEventBus(handlers);
    }

    /**
     * Publikuje zdarzenie przez ApplicationEventBus.
     *
     * Publikacja jest synchroniczna:
     * - handler jest wywoływany w tym samym wątku,
     * - wyjątek z handlera przerywa publikację,
     * - event nie jest nigdzie trwale zapisywany.
     */
    @Override
    public void publish(DomainEvent event) {
        delegate.publish(event);
    }
}