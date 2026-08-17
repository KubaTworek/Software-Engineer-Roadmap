package com.example.ecommerce.promotion;

import com.example.ecommerce.promotion.dto.PromotionDtos;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller odpowiedzialny za publiczne API kalkulacji promocji.
 *
 * Ten endpoint pozwala przeliczyć koszyk lub zestaw pozycji zakupowych
 * przez silnik promocji.
 *
 * W aplikacji e-commerce promocje mogą wpływać na:
 * - subtotal produktów,
 * - koszt dostawy,
 * - kupony rabatowe,
 * - rabaty kategorii,
 * - rabaty produktowe,
 * - promocje typu BUY_X_GET_Y.
 *
 * Controller nie zawiera logiki promocyjnej.
 * Całość deleguje do PromotionEngine.
 */
@RestController
@RequestMapping("/api/promotions")
public class PromotionController {

    /**
     * Silnik promocji.
     *
     * Odpowiada za właściwą logikę:
     * - pobranie aktywnych promocji,
     * - sprawdzenie kuponu,
     * - dopasowanie promocji do pozycji,
     * - wyliczenie rabatu,
     * - uwzględnienie kosztu dostawy,
     * - zwrócenie finalnej ceny i listy zastosowanych obniżek.
     */
    private final PromotionEngine engine;

    /**
     * Constructor injection.
     *
     * Controller potrzebuje tylko PromotionEngine.
     * Nie powinien samodzielnie pobierać promocji ani liczyć rabatów.
     */
    public PromotionController(PromotionEngine engine) {
        this.engine = engine;
    }

    /**
     * Przelicza cenę z uwzględnieniem aktywnych promocji i opcjonalnego kuponu.
     *
     * Endpoint:
     * POST /api/promotions/price
     *
     * Request zwykle zawiera:
     * - listę pozycji,
     * - productId,
     * - productVariantId,
     * - categoryId,
     * - quantity,
     * - unitPrice,
     * - couponCode,
     * - shippingAmount.
     *
     * @Valid:
     * uruchamia walidację DTO, np. czy pozycje mają poprawną ilość i cenę.
     *
     * Ważne:
     * frontend może poprosić o kalkulację promocji,
     * ale finalna cena zamówienia powinna być liczona po stronie backendu
     * podczas checkoutu, nie po stronie klienta.
     *
     * Ten endpoint jest przydatny dla:
     * - podglądu rabatów w koszyku,
     * - sprawdzenia kuponu,
     * - pokazania użytkownikowi rozbicia ceny przed checkoutem.
     */
    @PostMapping("/price")
    public PromotionDtos.PriceResponse price(
            @Valid @RequestBody PromotionDtos.PriceRequest request
    ) {
        return engine.price(request);
    }
}