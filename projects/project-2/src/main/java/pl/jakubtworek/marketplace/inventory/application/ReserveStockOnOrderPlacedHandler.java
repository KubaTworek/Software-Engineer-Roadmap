package pl.jakubtworek.marketplace.inventory.application;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.marketplace.inventory.domain.StockReservationFailed;
import pl.jakubtworek.marketplace.inventory.domain.StockReserved;
import pl.jakubtworek.marketplace.integration.contracts.OrderPlacedIntegrationEvent;
import pl.jakubtworek.marketplace.shared.events.DomainEventHandler;
import pl.jakubtworek.marketplace.shared.events.EventPublisher;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;

/**
 * Handler reagujący na zdarzenie OrderPlaced.
 *
 * Ten handler należy do modułu Inventory i odpowiada za próbę zarezerwowania stocku
 * dla produktów znajdujących się w nowo złożonym zamówieniu.
 *
 * Ważna zasada architektoniczna:
 * - moduł Ordering nie wywołuje bezpośrednio modułu Inventory,
 * - Ordering publikuje fakt biznesowy OrderPlaced,
 * - Inventory reaguje na ten fakt i sam decyduje, czy stock można zarezerwować.
 *
 * Dzięki temu moduły komunikują się przez zdarzenia, a nie przez bezpośrednie zależności
 * implementacyjne między use case’ami.
 */
@Component
public class ReserveStockOnOrderPlacedHandler implements DomainEventHandler<OrderPlacedIntegrationEvent> {

    /**
     * Port repozytorium stocku.
     *
     * Handler potrzebuje dostępu do aktualnego stanu magazynowego, ale zależy od abstrakcji,
     * a nie od konkretnej implementacji bazy danych.
     */
    private final StockRepository repository;

    /**
     * Publisher zdarzeń.
     *
     * Po zakończeniu próby rezerwacji handler publikuje jedno z dwóch zdarzeń:
     * - StockReserved, jeśli rezerwacja się udała,
     * - StockReservationFailed, jeśli nie da się zarezerwować stocku.
     *
     * W obecnej architekturze EventPublisher może zapisywać zdarzenia do outboxa,
     * dzięki czemu publikacja jest niezawodniejsza niż bezpośrednie wołanie handlerów.
     */
    private final EventPublisher eventPublisher;
    private final StockReservationRepository reservations;

    @Autowired
    public ReserveStockOnOrderPlacedHandler(
            StockRepository repository,
            EventPublisher eventPublisher,
            StockReservationRepository reservations
    ) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
        this.reservations = reservations;
    }

    /** Wygodny konstruktor laboratoriów, w których kompensacja nie jest przedmiotem testu. */
    public ReserveStockOnOrderPlacedHandler(StockRepository repository, EventPublisher eventPublisher) {
        this(repository, eventPublisher, new pl.jakubtworek.marketplace.inventory.infrastructure.InMemoryStockReservationRepository());
    }

    /**
     * Informuje event bus, jaki typ zdarzenia obsługuje ten handler.
     *
     * Dzięki temu mechanizm dispatchowania może dopasować OrderPlaced do tego handlera.
     */
    @Override
    public Class<OrderPlacedIntegrationEvent> eventType() {
        return OrderPlacedIntegrationEvent.class;
    }

    /**
     * Obsługuje zdarzenie OrderPlaced.
     *
     * Cała operacja jest wykonywana w transakcji:
     * - sprawdzenie dostępności stocku,
     * - rezerwacja stocku,
     * - zapis zmienionych StockItem,
     * - zapis/publikacja zdarzenia wynikowego.
     *
     * Handler działa dwuetapowo:
     *
     * 1. Najpierw waliduje, czy wszystkie linie zamówienia można zarezerwować.
     *    Jeśli choć jedna linia nie może zostać zarezerwowana, publikuje StockReservationFailed
     *    i nie rezerwuje niczego.
     *
     * 2. Dopiero gdy wszystkie linie są możliwe do zarezerwowania, wykonuje właściwą
     *    rezerwację i publikuje StockReserved.
     *
     * Dzięki temu unikamy częściowej rezerwacji stocku dla zamówienia.
     */
    @Override
    @Transactional
    public void handle(OrderPlacedIntegrationEvent event) {
        /*
         * Duplikaty produktu w jednym zamówieniu agregujemy przed walidacją. Naiwne
         * sprawdzenie każdej linii osobno mogłoby zaakceptować 2x6 sztuk przy stanie 10.
         * Stała kolejność UUID zapobiega deadlockowi, gdy dwa zamówienia blokują te same
         * produkty w przeciwnej kolejności.
         */
        var requested = new LinkedHashMap<UUID, Integer>();
        event.lines().forEach(line -> requested.merge(line.productId(), line.quantity(), Math::addExact));
        var orderedRequests = requested.entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey(Comparator.comparing(UUID::toString)))
                .toList();
        var lockedItems = new LinkedHashMap<UUID, pl.jakubtworek.marketplace.inventory.domain.StockItem>();
        /*
         * Pierwsza pętla tylko sprawdza dostępność.
         *
         * Nie zmieniamy jeszcze stanu magazynu. To ważne, bo jeśli druga albo trzecia linia
         * zamówienia okaże się niemożliwa do zarezerwowania, nie chcemy zostawić częściowo
         * zarezerwowanego stocku dla wcześniejszych linii.
         */
        for (var line : orderedRequests) {
            var item = repository.findByProductIdForUpdate(line.getKey());

            /*
             * Brak StockItem oznacza, że system nie ma żadnego stanu magazynowego
             * dla danego produktu. W takim przypadku rezerwacja nie może się udać.
             */
            if (item.isEmpty()) {
                eventPublisher.publish(StockReservationFailed.now(
                        event.aggregateId(),
                        line.getKey(),
                        "Missing stock item",
                        event.correlationId(),
                        event.eventId()
                ));
                return;
            }

            /*
             * StockItem istnieje, ale nie ma wystarczającej dostępnej ilości.
             *
             * Publikujemy zdarzenie porażki, żeby moduł Ordering mógł odrzucić zamówienie
             * albo uruchomić inny proces kompensacyjny.
             */
            if (!item.get().canReserve(line.getValue())) {
                eventPublisher.publish(StockReservationFailed.now(
                        event.aggregateId(),
                        line.getKey(),
                        "Not enough stock",
                        event.correlationId(),
                        event.eventId()
                ));
                return;
            }
            lockedItems.put(line.getKey(), item.get());
        }

        /*
         * Druga pętla wykonuje właściwą rezerwację.
         *
         * Na tym etapie wiemy już, że każda linia zamówienia ma dostępny stock,
         * więc można bezpiecznie zmienić stan magazynu.
         */
        List<StockReserved.Line> reservedLines = new ArrayList<>();

        for (var line : orderedRequests) {
            var item = lockedItems.get(line.getKey());

            /*
             * Rezerwujemy stock bez publikowania eventu bezpośrednio z agregatu.
             *
             * W tym flow to handler odpowiada za opublikowanie jednego zbiorczego eventu
             * StockReserved dla całego zamówienia. Gdyby każda linia publikowała własne
             * zdarzenie, proces po stronie Ordering byłby bardziej skomplikowany.
             */
            item.reserveWithoutPublishingEvent(line.getValue());

            /*
             * Zapisujemy zmieniony agregat StockItem.
             *
             * W implementacji JDBC/JPA ten zapis powinien być częścią tej samej transakcji,
             * w której zapisujemy event wynikowy do outboxa.
             */
            repository.save(item);

            reservedLines.add(new StockReserved.Line(
                    line.getKey(),
                    line.getValue()
            ));
        }

        /*
         * Publikujemy informację, że stock dla całego zamówienia został zarezerwowany.
         *
         * correlationId pozwala śledzić cały flow zamówienia przez moduły.
         * causationId ustawiony na event.eventId() mówi, że StockReserved powstał
         * jako reakcja na konkretne OrderPlaced.
         */
        reservations.record(event.aggregateId(), reservedLines.stream()
                .map(line -> new StockReservationRepository.Line(line.productId(), line.quantity()))
                .toList());
        eventPublisher.publish(StockReserved.now(
                event.aggregateId(),
                reservedLines,
                event.correlationId(),
                event.eventId()
        ));
    }
}
