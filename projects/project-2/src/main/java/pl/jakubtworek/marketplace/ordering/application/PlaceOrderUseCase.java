package pl.jakubtworek.marketplace.ordering.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.marketplace.catalog.domain.ProductId;
import pl.jakubtworek.marketplace.ordering.domain.*;
import pl.jakubtworek.marketplace.shared.events.EventPublisher;
import pl.jakubtworek.marketplace.shared.kernel.Money;

import java.util.List;
import java.util.UUID;

/**
 * Use case odpowiedzialny za złożenie zamówienia.
 *
 * Ta klasa należy do warstwy aplikacyjnej modułu Ordering.
 * Jej zadaniem jest orkiestracja operacji złożenia zamówienia:
 * - przyjęcie komendy z danymi wejściowymi,
 * - zamiana prostych typów z komendy na obiekty domenowe,
 * - utworzenie agregatu Order,
 * - zapis agregatu przez port repozytorium,
 * - publikacja zdarzeń wygenerowanych przez domenę.
 *
 * Use case nie powinien zawierać szczegółów HTTP, JDBC, JPA, Kafki ani struktury tabel.
 * Te elementy należą do adapterów infrastrukturalnych.
 */
@Service
public class PlaceOrderUseCase {

    /**
     * Port repozytorium zamówień.
     *
     * Warstwa aplikacyjna zależy od abstrakcji OrderRepository, a nie od konkretnej
     * implementacji zapisu. Dzięki temu repozytorium może być in-memory, JDBC, JPA
     * albo inne, bez zmiany tego use case’a.
     */
    private final OrderRepository repository;

    /**
     * Port publikowania zdarzeń.
     *
     * Agregat Order generuje zdarzenia domenowe, np. OrderPlaced.
     * Use case pobiera je z agregatu i przekazuje do EventPublisher.
     *
     * W aktualnej architekturze implementacją EventPublisher może być OutboxEventPublisher,
     * który zapisuje zdarzenia do tabeli outbox_events w tej samej transakcji co zamówienie.
     */
    private final EventPublisher eventPublisher;

    public PlaceOrderUseCase(
            OrderRepository repository,
            EventPublisher eventPublisher
    ) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Obsługuje komendę złożenia zamówienia.
     *
     * Granica transakcji znajduje się na poziomie use case’a, ponieważ złożenie zamówienia
     * jest pojedynczą operacją aplikacyjną. W jednej transakcji powinny znaleźć się:
     * - zapis agregatu Order,
     * - zapis zdarzeń do outboxa.
     *
     * Przepływ:
     * 1. Linie z komendy są mapowane na domenowe OrderLine.
     * 2. customerId jest opakowywany w domenowy CustomerId.
     * 3. Agregat Order jest tworzony przez metodę fabrykującą Order.place(...).
     * 4. Order zostaje zapisany przez repozytorium.
     * 5. Zdarzenia domenowe są kopiowane i czyszczone z agregatu.
     * 6. Zdarzenia są przekazywane do EventPublisher.
     * 7. Use case zwraca identyfikator utworzonego zamówienia.
     */
    @Transactional
    public OrderId handle(Command command) {
        /*
         * Mapowanie danych wejściowych na obiekty domenowe.
         *
         * Kontroler przekazuje do use case’a proste typy, takie jak UUID i String.
         * Tutaj zamieniamy je na typy domenowe: ProductId, Money i OrderLine.
         */
        List<OrderLine> lines = command.lines().stream()
                .map(line -> new OrderLine(
                        ProductId.of(line.productId()),
                        line.quantity(),
                        Money.of(line.unitAmount(), line.currency())
                ))
                .toList();

        /*
         * Utworzenie agregatu Order.
         *
         * To Order.place(...) powinien pilnować reguł domenowych, np.:
         * - zamówienie musi mieć przynajmniej jedną linię,
         * - ilość produktu musi być dodatnia,
         * - total zamówienia wynika z linii,
         * - po złożeniu zamówienia powstaje OrderPlaced.
         */
        Order order = Order.place(
                CustomerId.of(command.customerId()),
                lines,
                command.correlationId()
        );

        /*
         * Zapis agregatu.
         *
         * Przy implementacji JDBC/JPA zapis powinien być częścią tej samej transakcji
         * co publikacja eventów przez outbox.
         */
        repository.save(order);

        /*
         * Kopiujemy eventy przed publikacją.
         *
         * Jest to ważne, ponieważ po publikacji eventów agregat nie powinien nadal trzymać
         * zdarzeń, które zostały już przekazane dalej.
         */
        var events = List.copyOf(order.domainEvents());

        /*
         * Czyścimy eventy z agregatu po ich skopiowaniu.
         *
         * Chroni to przed przypadkowym ponownym opublikowaniem tych samych zdarzeń,
         * np. gdyby ten sam obiekt agregatu został ponownie zapisany albo użyty w teście.
         */
        order.clearDomainEvents();

        /*
         * Publikujemy zdarzenia przez port.
         *
         * W fazie outboxa nie oznacza to bezpośredniego wywołania kolejnych handlerów.
         * EventPublisher powinien zapisać zdarzenie do outboxa, a dopiero worker outboxa
         * opublikuje je dalej, np. do Kafki.
         */
        events.forEach(eventPublisher::publish);

        return order.id();
    }

    /**
     * Komenda wejściowa use case’a.
     *
     * Command należy do warstwy aplikacyjnej. Nie jest DTO HTTP, mimo że może mieć
     * podobne pola jak request w kontrolerze.
     *
     * Reprezentuje intencję:
     * "złóż zamówienie dla klienta z podanymi liniami i correlationId".
     */
    public record Command(
            UUID customerId,
            List<Line> lines,
            UUID correlationId
    ) {
    }

    /**
     * Linia zamówienia w komendzie use case’a.
     *
     * Przechowuje dane wejściowe potrzebne do stworzenia domenowego OrderLine.
     * W tej uproszczonej wersji cena jednostkowa przychodzi z API, ale produkcyjnie
     * zwykle powinna zostać pobrana z modułu Catalog albo Pricing, a nie być zaufana
     * bezpośrednio od klienta.
     */
    public record Line(
            UUID productId,
            int quantity,
            String unitAmount,
            String currency
    ) {
    }
}