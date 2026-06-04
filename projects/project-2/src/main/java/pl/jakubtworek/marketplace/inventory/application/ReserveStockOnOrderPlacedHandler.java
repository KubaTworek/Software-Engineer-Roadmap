package pl.jakubtworek.marketplace.inventory.application;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.marketplace.inventory.domain.StockReservationFailed;
import pl.jakubtworek.marketplace.inventory.domain.StockReserved;
import pl.jakubtworek.marketplace.ordering.domain.OrderPlaced;
import pl.jakubtworek.marketplace.shared.events.DomainEventHandler;
import pl.jakubtworek.marketplace.shared.events.EventPublisher;

import java.util.ArrayList;
import java.util.List;

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
public class ReserveStockOnOrderPlacedHandler implements DomainEventHandler<OrderPlaced> {

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

    public ReserveStockOnOrderPlacedHandler(
            StockRepository repository,
            EventPublisher eventPublisher
    ) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Informuje event bus, jaki typ zdarzenia obsługuje ten handler.
     *
     * Dzięki temu mechanizm dispatchowania może dopasować OrderPlaced do tego handlera.
     */
    @Override
    public Class<OrderPlaced> eventType() {
        return OrderPlaced.class;
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
    public void handle(OrderPlaced event) {
        /*
         * Pierwsza pętla tylko sprawdza dostępność.
         *
         * Nie zmieniamy jeszcze stanu magazynu. To ważne, bo jeśli druga albo trzecia linia
         * zamówienia okaże się niemożliwa do zarezerwowania, nie chcemy zostawić częściowo
         * zarezerwowanego stocku dla wcześniejszych linii.
         */
        for (OrderPlaced.Line line : event.lines()) {
            var item = repository.findByProductId(line.productId());

            /*
             * Brak StockItem oznacza, że system nie ma żadnego stanu magazynowego
             * dla danego produktu. W takim przypadku rezerwacja nie może się udać.
             */
            if (item.isEmpty()) {
                eventPublisher.publish(StockReservationFailed.now(
                        event.aggregateId(),
                        line.productId(),
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
            if (!item.get().canReserve(line.quantity())) {
                eventPublisher.publish(StockReservationFailed.now(
                        event.aggregateId(),
                        line.productId(),
                        "Not enough stock",
                        event.correlationId(),
                        event.eventId()
                ));
                return;
            }
        }

        /*
         * Druga pętla wykonuje właściwą rezerwację.
         *
         * Na tym etapie wiemy już, że każda linia zamówienia ma dostępny stock,
         * więc można bezpiecznie zmienić stan magazynu.
         */
        List<StockReserved.Line> reservedLines = new ArrayList<>();

        for (OrderPlaced.Line line : event.lines()) {
            var item = repository.findByProductId(line.productId())
                    .orElseThrow();

            /*
             * Rezerwujemy stock bez publikowania eventu bezpośrednio z agregatu.
             *
             * W tym flow to handler odpowiada za opublikowanie jednego zbiorczego eventu
             * StockReserved dla całego zamówienia. Gdyby każda linia publikowała własne
             * zdarzenie, proces po stronie Ordering byłby bardziej skomplikowany.
             */
            item.reserveWithoutPublishingEvent(line.quantity());

            /*
             * Zapisujemy zmieniony agregat StockItem.
             *
             * W implementacji JDBC/JPA ten zapis powinien być częścią tej samej transakcji,
             * w której zapisujemy event wynikowy do outboxa.
             */
            repository.save(item);

            reservedLines.add(new StockReserved.Line(
                    line.productId(),
                    line.quantity()
            ));
        }

        /*
         * Publikujemy informację, że stock dla całego zamówienia został zarezerwowany.
         *
         * correlationId pozwala śledzić cały flow zamówienia przez moduły.
         * causationId ustawiony na event.eventId() mówi, że StockReserved powstał
         * jako reakcja na konkretne OrderPlaced.
         */
        eventPublisher.publish(StockReserved.now(
                event.aggregateId(),
                reservedLines,
                event.correlationId(),
                event.eventId()
        ));
    }
}