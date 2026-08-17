package com.example.ecommerce.pricing;

import com.example.ecommerce.pricing.dto.PricingDtos;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller odpowiedzialny za API dynamic pricingu.
 *
 * Dynamic pricing pozwala wyliczyć cenę produktu na podstawie reguł biznesowych,
 * a nie tylko stałej ceny zapisanej w katalogu.
 *
 * Przykładowe reguły:
 * - podniesienie ceny przy wysokim popycie,
 * - obniżka przy wyprzedaży,
 * - cena zależna od niskiego stocku,
 * - override ceny przez sprzedawcę marketplace,
 * - reguła czasowa, np. weekendowa promocja.
 *
 * Controller nie zawiera logiki cenowej.
 * Deleguje obliczenie ceny do DynamicPricingService.
 */
@RestController
@RequestMapping("/api/pricing")
public class PricingController {

    /**
     * Serwis dynamic pricingu.
     *
     * Odpowiada za właściwą logikę:
     * - znalezienie pasujących reguł cenowych,
     * - wybranie reguły dla produktu/wariantu/kategorii,
     * - przeliczenie ceny bazowej,
     * - zwrócenie ceny finalnej i powodu zmiany ceny.
     */
    private final DynamicPricingService pricing;

    /**
     * Constructor injection.
     *
     * Controller potrzebuje tylko DynamicPricingService.
     * Nie powinien samodzielnie pobierać reguł cenowych ani liczyć ceny.
     */
    public PricingController(DynamicPricingService pricing) {
        this.pricing = pricing;
    }

    /**
     * Wylicza dynamiczną cenę dla produktu lub wariantu.
     *
     * Endpoint:
     * POST /api/pricing/dynamic
     *
     * Request powinien zawierać m.in.:
     * - productId,
     * - variantId,
     * - categoryId,
     * - basePrice.
     *
     * @Valid:
     * uruchamia walidację DTO, np. czy productId, variantId i basePrice są podane
     * oraz czy basePrice jest dodatnia.
     *
     * Kluczowe:
     * frontend nie powinien sam decydować o finalnej cenie.
     * Może wysłać dane do przeliczenia, ale źródłem logiki cenowej jest backend.
     *
     * W pełnym checkout flow dynamic pricing powinien zostać wpięty bezpośrednio
     * w kalkulację koszyka/zamówienia, żeby cena finalna była wyliczana po stronie systemu.
     */
    @PostMapping("/dynamic")
    public PricingDtos.DynamicPriceResponse price(
            @Valid @RequestBody PricingDtos.DynamicPriceRequest request
    ) {
        return pricing.price(request);
    }
}