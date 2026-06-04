package pl.jakubtworek.marketplace.inventory.domain;

import pl.jakubtworek.marketplace.shared.kernel.AggregateRoot;

import java.util.UUID;

/**
 * Agregat reprezentujący stan magazynowy konkretnego produktu.
 *
 * StockItem należy do modelu domenowego modułu Inventory.
 * Nie zależy od Springa, JPA, HTTP, Kafki ani innych szczegółów infrastruktury.
 *
 * Odpowiedzialności tej klasy:
 * - przechowywanie ilości dostępnej do rezerwacji,
 * - przechowywanie ilości już zarezerwowanej,
 * - pilnowanie reguł dotyczących dodawania i rezerwowania stocku,
 * - opcjonalne publikowanie zdarzeń domenowych na poziomie agregatu.
 */
public class StockItem extends AggregateRoot {

    /**
     * Identyfikator produktu, którego dotyczy ten stan magazynowy.
     *
     * W obecnej wersji używamy UUID bezpośrednio, ponieważ Inventory niekoniecznie musi znać
     * domenowy typ ProductId z modułu Catalog. To ogranicza sprzężenie między modułami.
     *
     * Można też rozważyć osobny value object w Inventory, np. InventoryProductId.
     */
    private final UUID productId;

    /**
     * Ilość produktu dostępna do zarezerwowania.
     *
     * Ta wartość maleje przy rezerwacji stocku i rośnie przy dodaniu stocku.
     */
    private int availableQuantity;

    /**
     * Ilość produktu już zarezerwowana przez zamówienia.
     *
     * Ta wartość rośnie przy rezerwacji. W późniejszym etapie można dodać operacje:
     * - potwierdzenia rezerwacji,
     * - zwolnienia rezerwacji,
     * - zdjęcia stocku po wysyłce.
     */
    private int reservedQuantity;

    /**
     * Prywatny konstruktor wymusza tworzenie agregatu przez nazwane metody fabrykujące.
     *
     * Konstruktor pilnuje podstawowego invariantu:
     * - ilość dostępna nie może być ujemna,
     * - ilość zarezerwowana nie może być ujemna.
     */
    private StockItem(UUID productId, int availableQuantity, int reservedQuantity) {
        if (availableQuantity < 0 || reservedQuantity < 0) {
            throw new IllegalArgumentException("quantity cannot be negative");
        }

        this.productId = productId;
        this.availableQuantity = availableQuantity;
        this.reservedQuantity = reservedQuantity;
    }

    /**
     * Tworzy nowy stan magazynowy dla produktu.
     *
     * Nowy StockItem:
     * - dotyczy konkretnego produktu,
     * - startuje z podaną ilością dostępną,
     * - nie ma jeszcze żadnych rezerwacji.
     */
    public static StockItem create(UUID productId, int quantity) {
        return new StockItem(productId, quantity, 0);
    }

    /**
     * Odtwarza stan z bazy danych.
     *
     * Ta metoda nie oznacza utworzenia nowego produktu biznesowo.
     * Służy do rekonstrukcji obiektu domenowego na podstawie danych zapisanych wcześniej.
     *
     * Różnica względem create(...):
     * - create(...) generuje nowe ID i ustawia ilość na 0,
     * - restore(...) przyjmuje istniejące ID i istniejącą ilość.
     */
    public static StockItem restore(UUID productId, int availableQuantity, int reservedQuantity) {
        return new StockItem(productId, availableQuantity, reservedQuantity);
    }

    /**
     * Dodaje ilość produktu do dostępnego stocku.
     *
     * To jest operacja domenowa, a nie zwykły setter.
     * Dzięki temu agregat sam pilnuje, że nie można dodać ilości zerowej ani ujemnej.
     */
    public void add(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }

        availableQuantity += quantity;
    }

    /**
     * Sprawdza, czy można zarezerwować wskazaną ilość produktu.
     *
     * Metoda nie zmienia stanu agregatu. Służy tylko do sprawdzenia warunku.
     *
     * Rzuca wyjątek dla ilości niedodatniej, bo takie zapytanie biznesowo nie ma sensu.
     */
    public boolean canReserve(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }

        return availableQuantity >= quantity;
    }

    /**
     * Rezerwuje stock bez publikowania zdarzenia domenowego.
     *
     * Ta metoda jest używana przez handler aplikacyjny, który chce opublikować jedno zbiorcze
     * zdarzenie StockReserved dla całego zamówienia, zamiast osobnego eventu dla każdej linii.
     *
     * Operacja:
     * - zmniejsza ilość dostępną,
     * - zwiększa ilość zarezerwowaną.
     */
    public void reserveWithoutPublishingEvent(int quantity) {
        if (!canReserve(quantity)) {
            throw new IllegalStateException("not enough stock");
        }

        availableQuantity -= quantity;
        reservedQuantity += quantity;
    }

    /**
     * Rezerwuje stock i publikuje zdarzenie domenowe na poziomie agregatu.
     *
     * Ta metoda jest zachowana głównie dla testów jednostkowych agregatu.
     * W głównym flow aplikacyjnym handler OrderPlaced publikuje jedno zdarzenie StockReserved
     * dla całego zamówienia, a nie wiele zdarzeń dla pojedynczych linii.
     *
     * Jeśli nie ma wystarczającego stocku:
     * - agregat rejestruje StockReservationFailed,
     * - metoda zwraca false.
     *
     * Jeśli rezerwacja się uda:
     * - agregat zmienia swój stan,
     * - rejestruje StockReserved,
     * - metoda zwraca true.
     */
    public boolean reserve(UUID orderId, int quantity, UUID correlationId, UUID causationId) {
        if (!canReserve(quantity)) {
            registerEvent(StockReservationFailed.now(
                    orderId,
                    productId,
                    "Not enough stock",
                    correlationId,
                    causationId
            ));
            return false;
        }

        reserveWithoutPublishingEvent(quantity);

        registerEvent(StockReserved.now(
                orderId,
                java.util.List.of(new StockReserved.Line(productId, quantity)),
                correlationId,
                causationId
        ));

        return true;
    }

    /**
     * Zwraca identyfikator produktu, którego dotyczy stan magazynowy.
     */
    public UUID productId() {
        return productId;
    }

    /**
     * Zwraca ilość dostępną do rezerwacji.
     */
    public int availableQuantity() {
        return availableQuantity;
    }

    /**
     * Zwraca ilość już zarezerwowaną.
     */
    public int reservedQuantity() {
        return reservedQuantity;
    }
}