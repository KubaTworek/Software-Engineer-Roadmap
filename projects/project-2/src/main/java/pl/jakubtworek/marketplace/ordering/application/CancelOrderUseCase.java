package pl.jakubtworek.marketplace.ordering.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.marketplace.ordering.domain.OrderId;
import pl.jakubtworek.marketplace.shared.events.EventPublisher;

import java.util.List;
import java.util.UUID;

/**
 * Use case odpowiedzialny za anulowanie zamówienia.
 *
 * Ta klasa należy do warstwy aplikacyjnej modułu Ordering.
 * Jej zadaniem jest orkiestracja pojedynczej operacji aplikacyjnej:
 * - odnalezienie zamówienia,
 * - wykonanie operacji domenowej na agregacie Order,
 * - zapisanie zmienionego agregatu,
 * - opublikowanie zdarzeń domenowych wygenerowanych przez agregat.
 *
 * Use case nie powinien zawierać reguł biznesowych typu:
 * - czy dane zamówienie można anulować,
 * - z jakiego statusu można przejść do CANCELLED,
 * - jakie zdarzenie powinno powstać po anulowaniu.
 *
 * Takie reguły powinny znajdować się w agregacie Order.
 */
@Service
public class CancelOrderUseCase {

    /**
     * Port repozytorium zamówień.
     *
     * Use case zależy od abstrakcji, nie od konkretnej implementacji bazy danych.
     * Dzięki temu OrderRepository może być implementowane przez adapter in-memory,
     * JDBC, JPA albo inny mechanizm trwałości.
     */
    private final OrderRepository repository;

    /**
     * Port publikowania zdarzeń.
     *
     * W aktualnej architekturze implementacją tego portu może być np. OutboxEventPublisher,
     * który nie publikuje zdarzenia bezpośrednio do handlerów, tylko zapisuje je do outboxa.
     *
     * To pozwala zapisać zmianę zamówienia i event w ramach jednej transakcji.
     */
    private final EventPublisher eventPublisher;

    public CancelOrderUseCase(
            OrderRepository repository,
            EventPublisher eventPublisher
    ) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Anuluje zamówienie.
     *
     * Granica transakcji znajduje się na poziomie use case’a, ponieważ cała operacja
     * powinna być atomowa:
     * - odczyt zamówienia,
     * - zmiana stanu agregatu,
     * - zapis agregatu,
     * - zapis zdarzeń do outboxa.
     *
     * Przepływ:
     * 1. UUID z warstwy API zostaje opakowany w domenowy OrderId.
     * 2. Repozytorium odczytuje agregat Order.
     * 3. Agregat wykonuje operację cancel(...), pilnując własnych invariantów.
     * 4. Zmieniony agregat zostaje zapisany.
     * 5. Zdarzenia domenowe są kopiowane i czyszczone z agregatu.
     * 6. Skopiowane zdarzenia są publikowane przez EventPublisher.
     *
     * correlationId służy do śledzenia całego flow w logach, outboxie, Kafce i konsumentach.
     */
    @Transactional
    public void handle(UUID orderId, UUID correlationId) {
        var order = repository.findById(OrderId.of(orderId))
                .orElseThrow();

        /*
         * Operacja domenowa.
         *
         * To agregat Order powinien zdecydować, czy anulowanie jest dozwolone
         * w aktualnym statusie. Use case tylko przekazuje intencję.
         *
         * Drugi argument jest causationId.
         * Tutaj przekazujesz null, bo anulowanie pochodzi bezpośrednio z komendy HTTP,
         * a nie jako reakcja na wcześniejsze zdarzenie.
         */
        order.cancel(correlationId, null);

        /*
         * Zapis zmienionego agregatu.
         *
         * Przy implementacji JDBC/JPA ten zapis powinien wykonać się w tej samej
         * transakcji co zapis eventów do outboxa.
         */
        repository.save(order);

        /*
         * Kopiujemy eventy przed ich publikacją.
         *
         * To ważne, bo publisher/handler może uruchomić kolejne operacje, a agregat nie
         * powinien przypadkowo opublikować tych samych eventów drugi raz.
         */
        var events = List.copyOf(order.domainEvents());

        /*
         * Czyścimy eventy po skopiowaniu.
         *
         * Dzięki temu ponowny zapis albo dalsze użycie tego samego obiektu agregatu
         * nie spowoduje ponownej publikacji tych samych zdarzeń.
         */
        order.clearDomainEvents();

        /*
         * Publikujemy zdarzenia przez port.
         *
         * W fazie outboxa to zwykle oznacza zapis do tabeli integration.outbox_events,
         * a nie natychmiastową wysyłkę do Kafki.
         */
        events.forEach(eventPublisher::publish);
    }
}