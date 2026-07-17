package com.example.ecommerce.warehouse;

import com.example.ecommerce.catalog.ProductVariant;
import com.example.ecommerce.catalog.ProductVariantRepository;
import com.example.ecommerce.common.ApiException;
import com.example.ecommerce.outbox.OutboxService;
import com.example.ecommerce.warehouse.dto.WarehouseDtos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Serwis domenowy odpowiedzialny za obsługę wielu magazynów.
 *
 * W klasycznym inventory aplikacja może mieć jeden globalny stan produktu.
 * Ten serwis dodaje warstwę multi-warehouse, czyli stock rozbity na konkretne lokalizacje.
 *
 * Odpowiada za:
 * - tworzenie magazynów,
 * - ustawianie stocku wariantu w konkretnym magazynie,
 * - pobieranie listy magazynów,
 * - pobieranie stocku wariantu w podziale na magazyny,
 * - publikację eventów przez outbox.
 *
 * To jest baza pod późniejsze funkcje:
 * - routing zamówienia do najbliższego magazynu,
 * - fulfillment,
 * - WMS,
 * - click & collect,
 * - stock regionalny,
 * - marketplace seller warehouses.
 */
@Service
public class WarehouseService {

    /**
     * Repozytorium magazynów.
     *
     * Przechowuje dane lokalizacji magazynowych:
     * kod, nazwę, kraj, miasto i adres.
     */
    private final WarehouseRepository warehouses;

    /**
     * Repozytorium stocku magazynowego.
     *
     * WarehouseStock przechowuje ilości dla pary:
     * warehouse + productVariant.
     *
     * Dzięki temu ten sam wariant produktu może mieć różny stock
     * w różnych magazynach.
     */
    private final WarehouseStockRepository stock;

    /**
     * Repozytorium wariantów produktu.
     *
     * Stock ustawiamy dla konkretnego ProductVariant,
     * bo klient kupuje konkretny wariant: SKU, rozmiar, kolor, konfigurację.
     */
    private final ProductVariantRepository variants;

    /**
     * Serwis outbox.
     *
     * Po zmianach magazynowych zapisujemy eventy, żeby inne procesy mogły
     * zareagować asynchronicznie:
     * - WMS,
     * - ERP,
     * - search-indexer,
     * - analytics,
     * - system fulfillment.
     */
    private final OutboxService outbox;

    /**
     * Constructor injection.
     *
     * Serwis potrzebuje repozytoriów magazynów, stocku, wariantów oraz outboxa.
     */
    public WarehouseService(
            WarehouseRepository warehouses,
            WarehouseStockRepository stock,
            ProductVariantRepository variants,
            OutboxService outbox
    ) {
        this.warehouses = warehouses;
        this.stock = stock;
        this.variants = variants;
        this.outbox = outbox;
    }

    /**
     * Tworzy nowy magazyn.
     *
     * Flow:
     * 1. Tworzy encję Warehouse z kodem, nazwą i adresem.
     * 2. Zapisuje magazyn w bazie.
     * 3. Publikuje event WarehouseCreated do outboxa.
     * 4. Zwraca DTO magazynu.
     *
     * To jest operacja adminowa lub operacyjna.
     */
    @Transactional
    public WarehouseDtos.WarehouseResponse create(WarehouseDtos.CreateWarehouseRequest request) {
        /*
         * Kod magazynu powinien być stabilnym identyfikatorem biznesowym,
         * np. WAW-01, KRK-01, BER-01.
         *
         * W modelu repozytorium/encji warto wymusić unikalność code.
         */
        Warehouse warehouse = warehouses.save(
                new Warehouse(
                        request.code(),
                        request.name(),
                        request.country(),
                        request.city(),
                        request.address()
                )
        );

        /*
         * Event WarehouseCreated.
         *
         * Może zostać użyty do synchronizacji magazynu z ERP/WMS
         * albo do aktualizacji systemów operacyjnych.
         */
        outbox.saveEvent(
                "Warehouse",
                warehouse.getId().toString(),
                "WarehouseCreated",
                Map.of(
                        "warehouseId", warehouse.getId(),
                        "code", warehouse.getCode()
                )
        );

        return toResponse(warehouse);
    }

    /**
     * Ustawia stock konkretnego wariantu w konkretnym magazynie.
     *
     * Flow:
     * 1. Pobierz magazyn.
     * 2. Pobierz wariant produktu.
     * 3. Znajdź istniejący WarehouseStock dla warehouse + variant.
     * 4. Jeśli nie istnieje, utwórz nowy rekord stocku.
     * 5. Ustaw availableQuantity.
     * 6. Zapisz stock.
     * 7. Opublikuj event WarehouseStockUpdated.
     * 8. Zwróć DTO stocku.
     *
     * To jest operacja adminowa albo integracyjna, np. z WMS.
     */
    @Transactional
    public WarehouseDtos.WarehouseStockResponse setStock(WarehouseDtos.SetWarehouseStockRequest request) {
        /*
         * Sprawdzamy, czy magazyn istnieje.
         *
         * Nie ustawiamy stocku dla nieistniejącej lokalizacji.
         */
        Warehouse warehouse = warehouses.findById(request.warehouseId())
                .orElseThrow(() -> ApiException.notFound("Warehouse not found"));

        /*
         * Sprawdzamy, czy wariant produktu istnieje.
         *
         * Stock jest przypisany do wariantu, nie do ogólnego produktu.
         */
        ProductVariant variant = variants.findById(request.productVariantId())
                .orElseThrow(() -> ApiException.notFound("Product variant not found"));

        /*
         * Szukamy stocku dla pary magazyn + wariant.
         *
         * Jeśli rekord już istnieje, aktualizujemy go.
         * Jeśli nie istnieje, tworzymy nowy z początkową ilością 0.
         *
         * Dzięki temu metoda działa jak upsert.
         */
        WarehouseStock item = stock.findByWarehouseIdAndVariantId(
                        warehouse.getId(),
                        variant.getId()
                )
                .orElseGet(() -> new WarehouseStock(
                        warehouse,
                        variant,
                        0
                ));

        /*
         * Ustawiamy dostępną ilość w tym konkretnym magazynie.
         *
         * Encja WarehouseStock powinna zabezpieczać się przed wartościami ujemnymi.
         */
        item.setAvailableQuantity(request.availableQuantity());

        /*
         * Zapisujemy nowy albo zaktualizowany stock.
         */
        stock.save(item);

        /*
         * Event WarehouseStockUpdated.
         *
         * Informuje downstream services, że dostępność wariantu zmieniła się
         * w konkretnym magazynie.
         *
         * Może to uruchomić:
         * - aktualizację search indexu,
         * - synchronizację z ERP,
         * - komunikację z WMS,
         * - przeliczenie dostępności regionalnej,
         * - invalidację cache katalogu.
         */
        outbox.saveEvent(
                "WarehouseStock",
                item.getId().toString(),
                "WarehouseStockUpdated",
                Map.of(
                        "warehouseId", warehouse.getId(),
                        "variantId", variant.getId(),
                        "availableQuantity", item.getAvailableQuantity()
                )
        );

        return toResponse(item);
    }

    /**
     * Zwraca listę magazynów.
     *
     * W obecnej wersji zwraca wszystkie magazyny.
     *
     * W produkcji warto dodać:
     * - filtrowanie po statusie ACTIVE,
     * - paginację,
     * - filtrowanie po kraju/regionie,
     * - rozdzielenie widoku publicznego i adminowego.
     */
    @Transactional(readOnly = true)
    public List<WarehouseDtos.WarehouseResponse> warehouses() {
        return warehouses.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Zwraca stock danego wariantu we wszystkich magazynach.
     *
     * Wynik jest sortowany malejąco po availableQuantity,
     * więc magazyny z największym stockiem są na początku.
     *
     * Użycia:
     * - panel admina,
     * - fulfillment,
     * - WMS,
     * - wybór magazynu do realizacji zamówienia,
     * - diagnoza dostępności produktu.
     */
    @Transactional(readOnly = true)
    public List<WarehouseDtos.WarehouseStockResponse> stockByVariant(Long variantId) {
        return stock.findByVariantIdOrderByAvailableQuantityDesc(variantId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Mapuje encję Warehouse na DTO odpowiedzi API.
     *
     * DTO zawiera podstawowe dane lokalizacji magazynowej:
     * - id,
     * - code,
     * - name,
     * - country,
     * - city,
     * - address.
     */
    public WarehouseDtos.WarehouseResponse toResponse(Warehouse warehouse) {
        return new WarehouseDtos.WarehouseResponse(
                warehouse.getId(),
                warehouse.getCode(),
                warehouse.getName(),
                warehouse.getCountry(),
                warehouse.getCity(),
                warehouse.getAddress()
        );
    }

    /**
     * Mapuje encję WarehouseStock na DTO odpowiedzi API.
     *
     * Odpowiedź pokazuje stock wariantu w konkretnym magazynie:
     * - warehouseId,
     * - warehouseCode,
     * - productVariantId,
     * - availableQuantity,
     * - reservedQuantity,
     * - sellableQuantity.
     *
     * sellableQuantity to ilość realnie możliwa do sprzedaży:
     *
     * availableQuantity - reservedQuantity
     */
    public WarehouseDtos.WarehouseStockResponse toResponse(WarehouseStock item) {
        return new WarehouseDtos.WarehouseStockResponse(
                item.getWarehouse().getId(),
                item.getWarehouse().getCode(),
                item.getVariant().getId(),
                item.getAvailableQuantity(),
                item.getReservedQuantity(),
                item.sellableQuantity()
        );
    }
}