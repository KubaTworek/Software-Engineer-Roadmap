package pl.jakubtworek.marketplace.ordering.domain;

import pl.jakubtworek.marketplace.shared.kernel.AggregateRoot;
import pl.jakubtworek.marketplace.shared.kernel.Money;

import java.util.List;
import java.util.UUID;

/**
 * Agregat reprezentujący zamówienie.
 *
 * Order jest centralnym agregatem modułu Ordering. To on pilnuje reguł procesu zamówienia:
 * - zamówienie musi mieć przynajmniej jedną linię,
 * - zamówienie startuje w stanie PENDING,
 * - zamówienie może zostać potwierdzone dopiero po rezerwacji płatności i stocku,
 * - zamówienia zakończonego nie można anulować ani odrzucić,
 * - anulowanie generuje zdarzenie OrderCancelled,
 * - potwierdzenie generuje zdarzenie OrderConfirmed.
 *
 * Ta klasa należy do domeny, więc nie powinna zależeć od Springa, JPA, HTTP ani Kafki.
 */
public class Order extends AggregateRoot {

    /**
     * Tożsamość zamówienia.
     *
     * Identyfikator jest niemutowalny. Raz utworzone zamówienie nie powinno zmieniać ID.
     */
    private final OrderId id;

    /**
     * Identyfikator klienta, który złożył zamówienie.
     */
    private final CustomerId customerId;

    /**
     * Linie zamówienia.
     *
     * Lista jest kopiowana w konstruktorze, żeby zewnętrzny kod nie mógł zmienić jej
     * po utworzeniu agregatu.
     */
    private final List<OrderLine> lines;

    /**
     * Aktualny status zamówienia.
     *
     * Status jest zmieniany tylko przez metody domenowe, takie jak cancel(...),
     * reject(...), markPaymentReserved(...) i markStockReserved(...).
     */
    private OrderStatus status;

    /**
     * Informacja, czy płatność dla zamówienia została zarezerwowana.
     *
     * To jest część procesu potwierdzania zamówienia. Samo PaymentReserved nie wystarcza
     * do potwierdzenia zamówienia — potrzebna jest jeszcze rezerwacja stocku.
     */
    private boolean paymentReserved;

    /**
     * Informacja, czy stock dla zamówienia został zarezerwowany.
     *
     * Zamówienie może przejść do CONFIRMED dopiero wtedy, gdy paymentReserved i
     * stockReserved są prawdziwe.
     */
    private boolean stockReserved;

    /**
     * Prywatny konstruktor wymusza tworzenie agregatu przez metody fabrykujące.
     *
     * Konstruktor pilnuje podstawowego invariantu:
     * zamówienie musi mieć przynajmniej jedną linię.
     */
    private Order(
            OrderId id,
            CustomerId customerId,
            List<OrderLine> lines,
            OrderStatus status,
            boolean paymentReserved,
            boolean stockReserved
    ) {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("order must have at least one line");
        }

        this.id = id;
        this.customerId = customerId;
        this.lines = List.copyOf(lines);
        this.status = status;
        this.paymentReserved = paymentReserved;
        this.stockReserved = stockReserved;
    }

    /**
     * Składa nowe zamówienie.
     *
     * Nowe zamówienie:
     * - dostaje nowe ID,
     * - przypisywane jest do klienta,
     * - startuje w statusie PENDING,
     * - nie ma jeszcze zarezerwowanej płatności,
     * - nie ma jeszcze zarezerwowanego stocku,
     * - rejestruje zdarzenie OrderPlaced.
     *
     * correlationId pozwala śledzić cały flow zamówienia przez kolejne moduły.
     * causationId jest tutaj null, bo OrderPlaced powstaje bezpośrednio z komendy,
     * a nie jako reakcja na wcześniejsze zdarzenie.
     */
    public static Order place(CustomerId customerId, List<OrderLine> lines, UUID correlationId) {
        Order order = new Order(
                OrderId.newId(),
                customerId,
                lines,
                OrderStatus.PENDING,
                false,
                false
        );

        order.registerEvent(OrderPlaced.now(order, correlationId, null));

        return order;
    }

    /**
     * Odtwarza zamówienie z istniejącego stanu, np. z bazy danych.
     *
     * Ta metoda nie oznacza biznesowego złożenia nowego zamówienia.
     * Nie generuje OrderPlaced, tylko rekonstruuje agregat na podstawie wcześniej
     * zapisanego stanu.
     */
    public static Order restore(
            OrderId id,
            CustomerId customerId,
            List<OrderLine> lines,
            OrderStatus status,
            boolean paymentReserved,
            boolean stockReserved
    ) {
        return new Order(
                id,
                customerId,
                lines,
                status,
                paymentReserved,
                stockReserved
        );
    }

    /**
     * Oznacza, że płatność dla zamówienia została zarezerwowana.
     *
     * Jeśli zamówienie jest już anulowane albo odrzucone, ignorujemy event.
     * To chroni proces przed opóźnionymi zdarzeniami, które mogą dotrzeć po zmianie
     * statusu zamówienia.
     *
     * Po oznaczeniu płatności jako zarezerwowanej agregat sprawdza, czy można już
     * potwierdzić zamówienie.
     */
    public void markPaymentReserved(UUID correlationId, UUID causationId) {
        if (status == OrderStatus.CANCELLED || status == OrderStatus.REJECTED) {
            return;
        }

        this.paymentReserved = true;

        confirmIfReady(correlationId, causationId);
    }

    /**
     * Oznacza, że stock dla zamówienia został zarezerwowany.
     *
     * Jeśli zamówienie jest już anulowane albo odrzucone, ignorujemy event.
     * Dzięki temu opóźnione StockReserved nie cofnie zamówienia do poprawnego flow.
     *
     * Po oznaczeniu stocku jako zarezerwowanego agregat sprawdza, czy można już
     * potwierdzić zamówienie.
     */
    public void markStockReserved(UUID correlationId, UUID causationId) {
        if (status == OrderStatus.CANCELLED || status == OrderStatus.REJECTED) {
            return;
        }

        this.stockReserved = true;

        confirmIfReady(correlationId, causationId);
    }

    /**
     * Odrzuca zamówienie z podanym powodem.
     *
     * Przykładowe powody:
     * - płatność została odrzucona,
     * - nie udało się zarezerwować stocku,
     * - produkt nie istnieje w magazynie.
     *
     * Zamówienia zakończonego nie można już odrzucić.
     * Jeśli zamówienie jest już anulowane albo odrzucone, metoda nic nie robi.
     */
    public void reject(String reason) {
        if (status == OrderStatus.COMPLETED) {
            throw new IllegalStateException("completed order cannot be rejected");
        }

        if (status == OrderStatus.CANCELLED || status == OrderStatus.REJECTED) {
            return;
        }

        this.status = OrderStatus.REJECTED;
    }

    /**
     * Odrzuca zamówienie z domyślnym powodem.
     */
    public void reject() {
        reject("Rejected");
    }

    /**
     * Anuluje zamówienie.
     *
     * Zamówienia zakończonego nie można anulować.
     * Jeśli zamówienie jest już anulowane, metoda jest idempotentna i nic nie robi.
     *
     * Po anulowaniu agregat rejestruje zdarzenie OrderCancelled.
     */
    public void cancel(UUID correlationId, UUID causationId) {
        if (status == OrderStatus.COMPLETED) {
            throw new IllegalStateException("completed order cannot be cancelled");
        }

        if (status == OrderStatus.CANCELLED) {
            return;
        }

        this.status = OrderStatus.CANCELLED;

        registerEvent(OrderCancelled.now(this, correlationId, causationId));
    }

    /**
     * Potwierdza zamówienie, jeśli spełnione są wszystkie warunki.
     *
     * Zamówienie może przejść do CONFIRMED tylko wtedy, gdy:
     * - płatność została zarezerwowana,
     * - stock został zarezerwowany,
     * - zamówienie nie jest już CONFIRMED.
     *
     * Po przejściu do CONFIRMED agregat rejestruje OrderConfirmed.
     */
    private void confirmIfReady(UUID correlationId, UUID causationId) {
        if (paymentReserved && stockReserved && status != OrderStatus.CONFIRMED) {
            this.status = OrderStatus.CONFIRMED;
            registerEvent(OrderConfirmed.now(this, correlationId, causationId));
        }
    }

    /**
     * Oblicza całkowitą wartość zamówienia na podstawie linii.
     *
     * Total nie jest przechowywany jako osobne pole. Jest wyliczany z OrderLine,
     * dzięki czemu unikamy rozjazdu między liniami zamówienia a sumą.
     */
    public Money total() {
        return lines.stream()
                .map(OrderLine::total)
                .reduce(Money::add)
                .orElseThrow();
    }

    /**
     * Zwraca identyfikator zamówienia.
     */
    public OrderId id() {
        return id;
    }

    /**
     * Zwraca identyfikator klienta.
     */
    public CustomerId customerId() {
        return customerId;
    }

    /**
     * Zwraca linie zamówienia.
     *
     * Lista została zabezpieczona przez List.copyOf(...) w konstruktorze.
     */
    public List<OrderLine> lines() {
        return lines;
    }

    /**
     * Zwraca aktualny status zamówienia.
     */
    public OrderStatus status() {
        return status;
    }

    /**
     * Informuje, czy płatność została zarezerwowana.
     */
    public boolean paymentReserved() {
        return paymentReserved;
    }

    /**
     * Informuje, czy stock został zarezerwowany.
     */
    public boolean stockReserved() {
        return stockReserved;
    }
}