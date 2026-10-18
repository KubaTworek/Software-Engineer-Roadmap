package com.example.ecommerce.inventory;

import com.example.ecommerce.catalog.ProductVariant;
import com.example.ecommerce.common.ApiException;
import com.example.ecommerce.monitoring.BusinessMetrics;
import com.example.ecommerce.outbox.OutboxService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * Serwis domenowy odpowiedzialny za zarządzanie stanem magazynowym.
 *
 * To jedna z kluczowych klas w checkout flow.
 *
 * Odpowiada za:
 * - utworzenie rekordu inventory dla wariantu produktu,
 * - zwracanie dostępnej ilości produktu,
 * - aktualizację stocku przez admina,
 * - rezerwację stocku podczas checkoutu,
 * - potwierdzenie sprzedaży po udanej płatności,
 * - zwolnienie rezerwacji po anulowaniu lub nieudanej płatności,
 * - wygaszanie starych rezerwacji.
 *
 * Główna odpowiedzialność:
 * nie dopuścić do sprzedaży większej liczby produktów niż dostępna w magazynie.
 */
@Service
public class InventoryService {

    /**
     * Repozytorium stanów magazynowych.
     *
     * InventoryItem przechowuje:
     * - dostępny stock,
     * - zarezerwowany stock,
     * - powiązanie z wariantem produktu.
     */
    private final InventoryItemRepository items;

    /**
     * Repozytorium rezerwacji magazynowych.
     *
     * Rezerwacja powstaje podczas checkoutu.
     * Blokuje część stocku dla konkretnego zamówienia na określony czas.
     */
    private final InventoryReservationRepository reservations;

    /**
     * Serwis outbox.
     *
     * Zapisuje eventy domenowe dotyczące inventory.
     * Dzięki temu inne części systemu mogą reagować na zmiany stocku,
     * np. search-indexer, ERP, WMS albo analytics.
     */
    private final OutboxService outbox;
    private final Clock clock;
    private final BusinessMetrics metrics;

    /**
     * Constructor injection.
     *
     * InventoryService potrzebuje repozytoriów inventory oraz outboxa.
     */
    public InventoryService(
            InventoryItemRepository items,
            InventoryReservationRepository reservations,
            OutboxService outbox,
            Clock clock,
            BusinessMetrics metrics
    ) {
        this.items = items;
        this.reservations = reservations;
        this.outbox = outbox;
        this.clock = clock;
        this.metrics = metrics;
    }

    /**
     * Tworzy rekord inventory dla nowego wariantu produktu.
     *
     * Wywoływane zwykle podczas tworzenia produktu przez admina.
     *
     * initialStock jest zabezpieczony przez Math.max(0, initialStock),
     * więc nie da się utworzyć ujemnego stanu magazynowego.
     */
    @Transactional
    public void createInventoryItem(ProductVariant variant, int initialStock) {
        items.save(
                new InventoryItem(
                        variant,
                        Math.max(0, initialStock)
                )
        );
    }

    /**
     * Zwraca ilość produktu możliwą do sprzedaży.
     *
     * sellableQuantity zwykle oznacza:
     *
     * availableQuantity - reservedQuantity
     *
     * Czyli:
     * - availableQuantity to całkowity stock dostępny w systemie,
     * - reservedQuantity to stock tymczasowo zablokowany przez checkouty,
     * - sellableQuantity to to, co można jeszcze sprzedać.
     *
     * Jeśli wariant nie ma rekordu inventory, zwracamy 0.
     * To bezpieczne domyślne zachowanie — lepiej ukryć produkt jako niedostępny
     * niż sprzedać coś bez stanu magazynowego.
     */
    public int availableQuantity(Long variantId) {
        return items.findByVariantId(variantId)
                .map(InventoryItem::sellableQuantity)
                .orElse(0);
    }

    /**
     * Aktualizuje dostępny stock wariantu produktu.
     *
     * To operacja adminowa albo integracyjna, np. z ERP/WMS.
     *
     * Po zmianie stocku czyścimy cache katalogu i searcha, bo dostępność produktu
     * jest pokazywana w odpowiedziach katalogowych.
     *
     * Event InventoryUpdated trafia do outboxa, żeby inne procesy mogły zareagować
     * na zmianę dostępności.
     */
    @Transactional
    @CacheEvict(
            value = {
                    "products",
                    "productBySlug",
                    "searchResults"
            },
            allEntries = true
    )
    public void updateStock(Long variantId, int availableQuantity) {
        InventoryItem item = items.findByVariantId(variantId)
                .orElseThrow(() -> ApiException.notFound("Inventory item not found"));

        int oldQuantity = item.getAvailableQuantity();

        /*
         * Nie pozwalamy ustawić ujemnego stocku.
         *
         * Jeśli integracja lub admin poda -10, zapisujemy 0.
         */
        item.setAvailableQuantity(Math.max(0, availableQuantity));

        /*
         * Event domenowy o zmianie stocku.
         *
         * Przydatny dla:
         * - search-indexera,
         * - cache invalidation,
         * - ERP/WMS,
         * - monitoringu,
         * - audytu zmian magazynowych.
         */
        outbox.saveEvent(
                "InventoryItem",
                variantId.toString(),
                "InventoryUpdated",
                Map.of(
                        "variantId", variantId,
                        "oldAvailableQuantity", oldQuantity,
                        "newAvailableQuantity", item.getAvailableQuantity()
                )
        );
    }

    /**
     * Rezerwuje stock dla zamówienia.
     *
     * Wywoływane w checkoutcie po utworzeniu zamówienia.
     *
     * Flow:
     * 1. Pobierz InventoryItem dla wariantu.
     * 2. Sprawdź, czy sellableQuantity wystarcza.
     * 3. Zwiększ reservedQuantity.
     * 4. Zapisz InventoryReservation z datą wygaśnięcia.
     * 5. Zapisz event InventoryReserved do outboxa.
     *
     * Jeśli brakuje stocku, rzucamy 409 Conflict.
     * To oznacza konflikt biznesowy: klient chce kupić więcej niż mamy dostępne.
     */
    @Transactional
    public void reserve(Long orderId, ProductVariant variant, int quantity) {
        InventoryItem item = lockedInventory(variant.getId());

        /*
         * Najważniejsza walidacja przeciwko oversellingowi.
         *
         * Nie rezerwujemy więcej niż aktualnie można sprzedać.
         */
        if (item.sellableQuantity() < quantity) {
            throw ApiException.conflict("Not enough stock for variant " + variant.getId());
        }

        /*
         * Blokujemy stock dla tego zamówienia.
         *
         * Produkt nie jest jeszcze sprzedany, ale nie powinien być dostępny
         * dla innych checkoutów.
         */
        item.reserve(quantity);

        /*
         * Rezerwacja wygasa po 15 minutach.
         *
         * Jeśli klient nie opłaci zamówienia, InventoryExpirationWorker
         * później zwolni tę rezerwację.
         */
        reservations.save(
                new InventoryReservation(
                        orderId,
                        variant,
                        quantity,
                        clock.instant().plus(15, ChronoUnit.MINUTES)
                )
        );

        /*
         * Event o rezerwacji inventory.
         *
         * Może być użyty np. przez WMS, żeby przygotować blokadę towaru
         * w systemie magazynowym.
         */
        outbox.saveEvent(
                "Order",
                orderId.toString(),
                "InventoryReserved",
                Map.of(
                        "orderId", orderId,
                        "variantId", variant.getId(),
                        "quantity", quantity
                )
        );
    }

    /**
     * Potwierdza rezerwacje stocku po udanej płatności.
     *
     * Wywoływane zwykle po payment success.
     *
     * Flow:
     * 1. Pobierz aktywne rezerwacje dla zamówienia.
     * 2. Dla każdej rezerwacji zmniejsz realny availableQuantity.
     * 3. Zmniejsz reservedQuantity.
     * 4. Oznacz rezerwację jako CONFIRMED.
     * 5. Zapisz event InventoryReservationConfirmed.
     *
     * Po tej operacji towar jest uznany za sprzedany.
     */
    @Transactional
    @CacheEvict(
            value = {
                    "products",
                    "productBySlug",
                    "searchResults"
            },
            allEntries = true
    )
    public void confirmReservations(Long orderId) {
        for (InventoryReservation reservation : reservations.findByOrderIdAndStatus(orderId, ReservationStatus.ACTIVE)) {
            if (reservation.isExpiredAt(clock.instant())) {
                throw ApiException.conflict("Inventory reservation expired before payment confirmation");
            }

            InventoryItem item = lockedInventory(reservation.getVariant().getId());

            item.confirmSale(reservation.getQuantity());
            reservation.confirm();
        }

        /*
         * Event informujący, że rezerwacje zamówienia zostały potwierdzone.
         *
         * Może uruchomić dalsze procesy:
         * - fulfillment,
         * - WMS pick/pack,
         * - aktualizację search indexu,
         * - ERP sync.
         */
        outbox.saveEvent(
                "Order",
                orderId.toString(),
                "InventoryReservationConfirmed",
                Map.of("orderId", orderId)
        );
    }

    /**
     * Zwalnia aktywne rezerwacje dla zamówienia.
     *
     * Wywoływane, gdy:
     * - płatność się nie udała,
     * - zamówienie anulowano,
     * - checkout został przerwany,
     * - trzeba ręcznie odblokować stock.
     *
     * Flow:
     * 1. Pobierz aktywne rezerwacje dla orderId.
     * 2. Zmniejsz reservedQuantity.
     * 3. Oznacz rezerwację jako RELEASED.
     * 4. Zapisz event InventoryReservationReleased.
     *
     * Po tej operacji stock wraca do puli sellableQuantity.
     */
    @Transactional
    @CacheEvict(
            value = {
                    "products",
                    "productBySlug",
                    "searchResults"
            },
            allEntries = true
    )
    public void releaseReservations(Long orderId) {
        for (InventoryReservation reservation : reservations.findByOrderIdAndStatus(orderId, ReservationStatus.ACTIVE)) {
            InventoryItem item = lockedInventory(reservation.getVariant().getId());

            item.release(reservation.getQuantity());
            reservation.release();
        }

        /*
         * Event o zwolnieniu rezerwacji.
         *
         * Może być użyty np. do powiadomienia WMS,
         * że nie trzeba już trzymać towaru dla tego zamówienia.
         */
        outbox.saveEvent(
                "Order",
                orderId.toString(),
                "InventoryReservationReleased",
                Map.of("orderId", orderId)
        );
    }

    /**
     * Wygasza przeterminowane rezerwacje.
     *
     * Wywoływane cyklicznie przez InventoryExpirationWorker.
     *
     * Cel:
     * jeśli klient nie dokończy płatności, stock nie może być zablokowany wiecznie.
     *
     * Flow:
     * 1. Pobierz aktywne rezerwacje, których expiresAt jest w przeszłości.
     * 2. Ogranicz liczbę przetwarzanych rekordów do batchSize.
     * 3. Dla każdej rezerwacji zwolnij stock.
     * 4. Oznacz rezerwację jako RELEASED.
     * 5. Jeśli coś zostało przetworzone, zapisz event batchowy.
     * 6. Zwróć liczbę wygaszonych rezerwacji.
     *
     * Cache katalogu jest czyszczony, bo dostępność produktu mogła się zmienić.
     */
    @Transactional
    @CacheEvict(
            value = {
                    "products",
                    "productBySlug",
                    "searchResults",
                    "catalogHome",
                    "productDetails"
            },
            allEntries = true
    )
    public int expireReservations(int batchSize) {
        /*
         * Repozytorium pobiera do 100 aktywnych rezerwacji po expiresAt.
         *
         * Dodatkowy limit batchSize pozwala kontrolować tempo pracy workera
         * z poziomu konfiguracji.
         */
        var expired = reservations.findTop100ByStatusAndExpiresAtBeforeOrderByIdAsc(
                ReservationStatus.ACTIVE,
                clock.instant()
        );

        int processed = 0;

        for (InventoryReservation reservation : expired.stream().limit(batchSize).toList()) {
            InventoryItem item = lockedInventory(reservation.getVariant().getId());

            /*
             * Zmniejszamy reservedQuantity i przywracamy stock do sprzedaży.
             */
            item.release(reservation.getQuantity());

            /*
             * Oznaczamy rezerwację jako RELEASED,
             * żeby nie została przetworzona ponownie w kolejnym cyklu.
             */
            reservation.expire();

            processed++;
        }

        /*
         * Event batchowy zapisujemy tylko, jeśli faktycznie coś wygasło.
         *
         * To ogranicza szum w outboxie i pozwala downstream services
         * reagować tylko na realne zmiany.
         */
        if (processed > 0) {
            outbox.saveEvent(
                    "InventoryReservation",
                    "batch",
                    "InventoryReservationsExpired",
                    Map.of("expiredCount", processed)
            );
        }

        return processed;
    }

    /**
     * Mierzy pełny czas pobrania rekordu z blokadą {@code SELECT ... FOR UPDATE}.
     * Wysokie p95/p99 tej metryki oznacza kontencję na popularnym SKU, nawet jeśli
     * sam checkout ostatecznie kończy się sukcesem. Metryka nie używa variantId
     * jako tagu, ponieważ SKU tworzyłoby nieograniczoną kardynalność.
     */
    private InventoryItem lockedInventory(Long variantId) {
        long startedAt = System.nanoTime();
        try {
            return items.findByVariantIdForUpdate(variantId)
                    .orElseThrow(() -> ApiException.notFound("Inventory item not found"));
        } finally {
            metrics.recordInventoryLockWait(Duration.ofNanos(System.nanoTime() - startedAt));
        }
    }
}
