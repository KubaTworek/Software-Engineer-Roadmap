package pl.jakubtworek.marketplace.integration.outbox;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.marketplace.shared.events.EventPublisher;
import pl.jakubtworek.marketplace.shared.kernel.DomainEvent;

/**
 * Publisher zapisujący zdarzenia domenowe do outboxa.
 *
 * Ta klasa jest implementacją portu EventPublisher używaną po wprowadzeniu outbox pattern.
 *
 * W przeciwieństwie do ApplicationEventBus:
 * - nie wywołuje handlerów bezpośrednio,
 * - nie wykonuje synchronicznego dispatchowania eventów,
 * - nie realizuje komunikacji między modułami od razu w tym samym stosie wywołań.
 *
 * Zamiast tego zapisuje zdarzenie do tabeli outbox_events przez OutboxEventRepository.
 * Następnie osobny worker może pobrać event z outboxa i opublikować go dalej,
 * np. do Kafki albo do lokalnego dispatchera.
 *
 * Dzięki temu zmiana agregatu i zapis eventu mogą wydarzyć się w jednej transakcji.
 * To chroni przed sytuacją, w której:
 * - zamówienie zostało zapisane,
 * - aplikacja padła przed publikacją eventu,
 * - reszta systemu nigdy nie dowiedziała się o zmianie.
 */
@Primary
@Component
public class OutboxEventPublisher implements EventPublisher {

    /**
     * Repozytorium outboxa.
     *
     * Odpowiada za trwały zapis OutboxEvent.
     * W zależności od profilu może to być implementacja in-memory albo JDBC/PostgreSQL.
     */
    private final OutboxEventRepository repository;

    /**
     * Mapper zamieniający DomainEvent na OutboxEvent.
     *
     * To tutaj zdarzenie domenowe zostaje przekształcone do postaci możliwej
     * do zapisania w outboxie, np. eventType, eventVersion, payload, correlationId,
     * causationId i aggregateId.
     */
    private final OutboxEventMapper mapper;

    /**
     * Konstruktor używany przez Springa.
     *
     * Wstrzykujemy gotowy OutboxEventMapper jako bean, zamiast tworzyć go ręcznie
     * przez new OutboxEventMapper(...). Dzięki temu Spring zarządza zależnościami,
     * a klasa ma tylko jeden publiczny konstruktor.
     */
    public OutboxEventPublisher(
            OutboxEventRepository repository,
            OutboxEventMapper mapper
    ) {
        this.repository = repository;
        this.mapper = mapper;
    }

    /**
     * Publikuje zdarzenie przez zapis do outboxa.
     *
     * Nazwa metody pochodzi z portu EventPublisher, ale w tej implementacji "publish"
     * oznacza zapisanie zdarzenia do outboxa, a nie natychmiastowe wywołanie handlerów.
     *
     * @Transactional sprawia, że zapis eventu może uczestniczyć w tej samej transakcji,
     * w której use case zapisuje agregat.
     *
     * Przykład:
     * - PlaceOrderUseCase zapisuje Order,
     * - następnie publikuje OrderPlaced,
     * - OutboxEventPublisher zapisuje OrderPlaced do outbox_events,
     * - commit transakcji zatwierdza zarówno Order, jak i event.
     */
    @Override
    @Transactional
    public void publish(DomainEvent event) {
        repository.save(mapper.toOutboxEvent(event));
    }
}