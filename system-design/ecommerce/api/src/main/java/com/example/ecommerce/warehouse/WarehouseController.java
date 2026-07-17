package com.example.ecommerce.warehouse;

import com.example.ecommerce.warehouse.dto.WarehouseDtos;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller odpowiedzialny za publiczne API magazynów.
 *
 * W Stage 4 aplikacja obsługuje wiele magazynów.
 * Oznacza to, że stan produktu może być rozbity na kilka lokalizacji,
 * np. Warszawa, Kraków, Berlin albo magazyn marketplace seller.
 *
 * Ten controller pozwala:
 * - pobrać listę magazynów,
 * - sprawdzić stock konkretnego wariantu produktu w podziale na magazyny.
 *
 * Controller nie zawiera logiki magazynowej.
 * Deleguje ją do WarehouseService.
 */
@RestController
@RequestMapping("/api/warehouses")
public class WarehouseController {

    /**
     * Serwis magazynów.
     *
     * Odpowiada za właściwą logikę:
     * - pobieranie magazynów,
     * - pobieranie stocku wariantu per magazyn,
     * - mapowanie encji Warehouse i WarehouseStock na DTO.
     */
    private final WarehouseService warehouses;

    /**
     * Constructor injection.
     *
     * Controller potrzebuje tylko WarehouseService.
     * Nie powinien bezpośrednio korzystać z repozytoriów magazynowych.
     */
    public WarehouseController(WarehouseService warehouses) {
        this.warehouses = warehouses;
    }

    /**
     * Zwraca listę magazynów.
     *
     * Endpoint:
     * GET /api/warehouses
     *
     * W aplikacji e-commerce lista magazynów może być używana np. przez:
     * - admin panel,
     * - panel operacyjny,
     * - system fulfillment,
     * - wybór lokalizacji wysyłki,
     * - debugowanie dostępności produktu.
     *
     * W obecnej wersji endpoint zwraca wszystkie magazyny.
     * Produkcyjnie można dodać filtrowanie po statusie ACTIVE,
     * kraju, regionie albo typie magazynu.
     */
    @GetMapping
    public List<WarehouseDtos.WarehouseResponse> warehouses() {
        return warehouses.warehouses();
    }

    /**
     * Zwraca stan konkretnego wariantu produktu w podziale na magazyny.
     *
     * Endpoint:
     * GET /api/warehouses/stock/{variantId}
     *
     * variantId oznacza konkretny wariant produktu, np. rozmiar, kolor albo SKU.
     *
     * Odpowiedź zwykle zawiera dla każdego magazynu:
     * - warehouseId,
     * - warehouseCode,
     * - productVariantId,
     * - availableQuantity,
     * - reservedQuantity,
     * - sellableQuantity.
     *
     * To jest ważne dla systemów typu:
     * - fulfillment,
     * - WMS,
     * - routing zamówień do najbliższego magazynu,
     * - pokazywanie dostępności lokalnej,
     * - obsługa multi-warehouse inventory.
     */
    @GetMapping("/stock/{variantId}")
    public List<WarehouseDtos.WarehouseStockResponse> stock(
            @PathVariable Long variantId
    ) {
        return warehouses.stockByVariant(variantId);
    }
}