package com.example.ecommerce.pricing;

import com.example.ecommerce.pricing.dto.PricingDtos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.time.Instant;

/**
 * Serwis domenowy odpowiedzialny za dynamic pricing.
 *
 * Dynamic pricing pozwala wyliczyć cenę finalną na podstawie reguł,
 * zamiast zawsze używać ceny bazowej z katalogu.
 *
 * Przykładowe zastosowania:
 * - obniżka ceny przy wyprzedaży,
 * - podniesienie ceny przy niskim stocku,
 * - cena specjalna dla konkretnego wariantu,
 * - reguła czasowa,
 * - override ceny przez marketplace seller.
 *
 * Ten serwis nie pobiera produktu z katalogu.
 * Dostaje basePrice w requestcie i na tej podstawie aplikuje pasującą regułę.
 */
@Service
public class DynamicPricingService {

    /**
     * Repozytorium reguł dynamic pricingu.
     *
     * Reguły mogą być przypięte do:
     * - produktu,
     * - wariantu produktu,
     * - kategorii,
     * - okna czasowego.
     */
    private final DynamicPriceRuleRepository rules;

    /**
     * Constructor injection.
     *
     * Serwis potrzebuje tylko repozytorium reguł cenowych.
     */
    public DynamicPricingService(DynamicPriceRuleRepository rules) {
        this.rules = rules;
    }

    /**
     * Wylicza cenę finalną dla produktu/wariantu.
     *
     * Flow:
     * 1. Pobierz aktualny czas.
     * 2. Pobierz aktywne reguły.
     * 3. Znajdź pierwszą regułę pasującą do productId, variantId, categoryId i czasu.
     * 4. Jeśli nie ma reguły, zwróć cenę bazową.
     * 5. Jeśli reguła ma fixedPrice, użyj fixedPrice.
     * 6. Jeśli nie ma fixedPrice, przemnóż basePrice przez multiplier.
     * 7. Zwróć cenę finalną i powód zmiany.
     *
     * @Transactional(readOnly = true):
     * metoda tylko czyta reguły, więc może działać na read-replice.
     */
    @Transactional(readOnly = true)
    public PricingDtos.DynamicPriceResponse price(PricingDtos.DynamicPriceRequest request) {
        /*
         * Aktualny czas jest potrzebny, bo reguły mogą mieć startsAt i endsAt.
         *
         * Dzięki temu można tworzyć reguły czasowe, np. promocję weekendową
         * albo sezonową podwyżkę/obniżkę ceny.
         */
        var now = Instant.now();

        /*
         * Pobieramy aktywne reguły i wybieramy pierwszą pasującą.
         *
         * appliesTo() sprawdza, czy reguła pasuje do:
         * - productId,
         * - variantId,
         * - categoryId,
         * - aktualnego czasu.
         *
         * Ważne:
         * obecna implementacja bierze pierwszą pasującą regułę.
         * W produkcji warto dodać priority albo specificity ranking,
         * np. wariant > produkt > kategoria > global.
         */
        var rule = rules.findByActiveTrue()
                .stream()
                .filter(r -> r.appliesTo(
                        request.productId(),
                        request.variantId(),
                        request.categoryId(),
                        now
                ))
                .findFirst();

        /*
         * Brak pasującej reguły oznacza, że obowiązuje cena bazowa.
         *
         * reason = BASE_PRICE jasno mówi frontendowi/analityce,
         * że dynamic pricing nie zmienił ceny.
         */
        if (rule.isEmpty()) {
            return new PricingDtos.DynamicPriceResponse(
                    request.productId(),
                    request.variantId(),
                    request.basePrice(),
                    request.basePrice(),
                    "BASE_PRICE"
            );
        }

        var selected = rule.get();

        /*
         * Reguła może działać na dwa sposoby:
         *
         * 1. fixedPrice:
         *    cena finalna jest ustawiona sztywno.
         *
         * 2. multiplier:
         *    cena bazowa jest mnożona przez współczynnik.
         *
         * Przykłady:
         * - multiplier = 0.90 oznacza 10% rabatu,
         * - multiplier = 1.10 oznacza 10% podwyżki,
         * - fixedPrice = 99.99 oznacza cenę ustawioną ręcznie.
         */
        var finalPrice = selected.getFixedPrice() != null
                ? selected.getFixedPrice()
                : request.basePrice()
                .multiply(selected.getMultiplier())
                .setScale(2, RoundingMode.HALF_UP);

        /*
         * Zwracamy cenę bazową i finalną.
         *
         * To jest ważne dla frontendu i analityki:
         * - frontend może pokazać przekreśloną cenę bazową,
         * - analytics może mierzyć wpływ reguł pricingowych,
         * - admin może debugować, dlaczego cena się zmieniła.
         */
        return new PricingDtos.DynamicPriceResponse(
                request.productId(),
                request.variantId(),
                request.basePrice(),
                finalPrice,
                selected.getType().name()
        );
    }

    /**
     * Tworzy nową regułę dynamic pricingu.
     *
     * To metoda typowo używana przez Admin API.
     *
     * Reguła może dotyczyć:
     * - konkretnego produktu,
     * - konkretnego wariantu,
     * - kategorii,
     * - zakresu czasu,
     * - fixedPrice albo multiplier.
     *
     * Zwracamy ID nowej reguły, żeby admin panel mógł później ją edytować
     * albo dezaktywować.
     */
    @Transactional
    public Long createRule(PricingDtos.CreateDynamicPriceRuleRequest request) {
        return rules.save(
                new DynamicPriceRule(
                        request.productId(),
                        request.variantId(),
                        request.categoryId(),
                        request.type(),
                        request.multiplier(),
                        request.fixedPrice(),
                        request.startsAt(),
                        request.endsAt()
                )
        ).getId();
    }
}