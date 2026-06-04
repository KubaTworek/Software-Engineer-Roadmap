package pl.jakubtworek.marketplace.shared.events;

import org.springframework.stereotype.Component;
import pl.jakubtworek.marketplace.shared.kernel.DomainEvent;

import java.util.List;

/**
 * Synchroniczny event bus na poziomie aplikacji.
 *
 * Ten komponent służy do publikowania zdarzeń wewnątrz modularnego monolitu.
 * Nie jest to Kafka, RabbitMQ ani żaden zewnętrzny broker wiadomości.
 *
 * Jego główny cel:
 * - pozwolić modułom reagować na zdarzenia domenowe,
 * - uniknąć bezpośrednich wywołań między modułami,
 * - utrzymać luźniejsze powiązanie między Ordering, Payment, Inventory itd.
 *
 * Przykład:
 * - Ordering publikuje OrderPlaced,
 * - Payment reaguje przez ReservePaymentOnOrderPlacedHandler,
 * - Inventory reaguje przez ReserveStockOnOrderPlacedHandler.
 *
 * To jest dobre rozwiązanie na wcześniejszym etapie projektu, zanim pojawi się outbox
 * i Kafka. Po wprowadzeniu outboxa ten komponent powinien być traktowany raczej jako
 * mechanizm lokalny/testowy albo dispatcher używany przez konsumentów, a nie jako główna
 * ścieżka niezawodnej komunikacji.
 */
@Component
public class ApplicationEventBus implements EventPublisher {

    /**
     * Lista wszystkich handlerów zdarzeń zarejestrowanych w Spring Context.
     *
     * Spring automatycznie wstrzykuje tutaj wszystkie beany implementujące
     * DomainEventHandler<?>.
     *
     * Robimy List.copyOf(...), żeby lista handlerów była niemodyfikowalna po utworzeniu
     * event busa.
     */
    private final List<DomainEventHandler<?>> handlers;

    public ApplicationEventBus(List<DomainEventHandler<?>> handlers) {
        this.handlers = List.copyOf(handlers);
    }

    /**
     * Publikuje zdarzenie do wszystkich handlerów obsługujących jego typ.
     *
     * Działanie jest synchroniczne:
     * - metoda publish(...) nie kończy się, dopóki wszystkie pasujące handlery nie zakończą pracy,
     * - jeśli handler rzuci wyjątek, wyjątek poleci w górę stosu,
     * - nie ma tutaj retry, DLQ, trwałości ani commitowania offsetu.
     *
     * To oznacza, że ten event bus nie gwarantuje niezawodności znanej z outboxa/Kafki.
     * Jest prostym mechanizmem dispatchowania zdarzeń w ramach jednego procesu JVM.
     */
    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void publish(DomainEvent event) {
        handlers.stream()
                /*
                 * Dopasowujemy handlery po typie zdarzenia.
                 *
                 * isAssignableFrom(...) pozwala handlerowi obsłużyć także podtypy zdarzenia,
                 * jeśli kiedyś pojawi się dziedziczenie eventów.
                 *
                 * Dla większości prostych domain eventów można by też użyć:
                 * handler.eventType().equals(event.getClass())
                 */
                .filter(handler -> handler.eventType().isAssignableFrom(event.getClass()))

                /*
                 * DomainEventHandler<?> ma typ generyczny, ale w runtime Java usuwa
                 * informację o generykach. Dlatego po wcześniejszym sprawdzeniu eventType()
                 * wykonujemy rzutowanie do surowego DomainEventHandler.
                 *
                 * @SuppressWarnings jest tutaj świadome: bezpieczeństwo typu zapewnia metoda
                 * eventType(), a nie sam mechanizm generyków.
                 */
                .forEach(handler -> ((DomainEventHandler) handler).handle(event));
    }
}