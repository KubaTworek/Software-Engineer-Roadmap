package com.example.ecommerce.promotion;

import com.example.ecommerce.common.ApiException;
import com.example.ecommerce.promotion.dto.PromotionDtos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Silnik promocji odpowiedzialny za przeliczenie ceny koszyka.
 *
 * PromotionEngine nie tworzy promocji i nie zarządza kuponami.
 * Od tego jest PromotionAdminService.
 *
 * Ta klasa odpowiada za runtime pricing:
 * - policzenie subtotalu,
 * - pobranie aktywnych promocji,
 * - zastosowanie promocji według priorytetu,
 * - obsługę stackowania promocji,
 * - obsługę kuponu,
 * - wyliczenie discountAmount,
 * - wyliczenie finalnego totalu,
 * - zwrócenie listy zastosowanych rabatów.
 *
 * W pełnym systemie checkout powinien używać tego silnika po stronie backendu,
 * żeby finalna cena zamówienia nie zależała od kalkulacji wykonanej na froncie.
 */
@Service
public class PromotionEngine {

    /**
     * Repozytorium promocji.
     *
     * Używane do pobrania aktywnych reguł promocyjnych,
     * np. procentowego rabatu, darmowej dostawy albo BUY_X_GET_Y.
     */
    private final PromotionRepository promotions;

    /**
     * Repozytorium kuponów.
     *
     * Używane wtedy, gdy klient poda couponCode.
     * Kupon wskazuje na konkretną promocję, która definiuje logikę rabatu.
     */
    private final CouponRepository coupons;

    /**
     * Constructor injection.
     *
     * Silnik potrzebuje tylko źródła promocji i kuponów.
     */
    public PromotionEngine(
            PromotionRepository promotions,
            CouponRepository coupons
    ) {
        this.promotions = promotions;
        this.coupons = coupons;
    }

    /**
     * Przelicza cenę koszyka z uwzględnieniem promocji i opcjonalnego kuponu.
     *
     * Flow:
     * 1. Policz subtotal z pozycji.
     * 2. Ustal shippingAmount.
     * 3. Pobierz aktywne promocje.
     * 4. Odfiltruj promocje poza oknem czasowym.
     * 5. Posortuj promocje po priority.
     * 6. Zastosuj promocje.
     * 7. Jeśli promocja nie jest stackable, przerwij dalsze naliczanie promocji.
     * 8. Jeśli podano couponCode, sprawdź kupon i nalicz jego rabat.
     * 9. Policz total = subtotal - discount + shipping.
     * 10. Zabezpiecz total przed zejściem poniżej zera.
     * 11. Zwróć rozbicie ceny i listę zastosowanych rabatów.
     *
     * @Transactional(readOnly = true):
     * metoda tylko czyta promocje i kupony, więc może działać na read-replice.
     */
    @Transactional(readOnly = true)
    public PromotionDtos.PriceResponse price(PromotionDtos.PriceRequest request) {
        /*
         * Subtotal liczymy z pozycji requestu.
         *
         * Każda linia:
         * unitPrice * quantity
         *
         * Ważne:
         * W produkcyjnym checkoutcie unitPrice nie powinno pochodzić bezkrytycznie
         * z frontendu. Backend powinien pobrać ceny z katalogu/pricingu i dopiero
         * wtedy przekazać je do PromotionEngine.
         */
        BigDecimal subtotal = request.lines()
                .stream()
                .map(line -> line.unitPrice().multiply(BigDecimal.valueOf(line.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        /*
         * Koszt dostawy może być null.
         *
         * Jeśli go nie podano, traktujemy shipping jako 0.
         * To pozwala używać silnika także do samego koszyka bez wybranej dostawy.
         */
        BigDecimal shipping = request.shippingAmount() == null
                ? BigDecimal.ZERO
                : request.shippingAmount();

        /*
         * Pobieramy aktywne promocje i dodatkowo sprawdzamy okno czasowe.
         *
         * PromotionStatus.ACTIVE oznacza, że promocja jest włączona,
         * ale isActiveAt(now) sprawdza jeszcze startsAt/endsAt.
         *
         * Sortowanie po priority ustala kolejność naliczania rabatów.
         */
        List<Promotion> activePromotions = promotions
                .findByStatusOrderByPriorityAsc(PromotionStatus.ACTIVE)
                .stream()
                .filter(promotion -> promotion.isActiveAt(Instant.now()))
                .sorted(Comparator.comparingInt(Promotion::getPriority))
                .toList();

        /*
         * Lista korekt cenowych zwracana do API.
         *
         * Dzięki niej frontend może pokazać klientowi:
         * - jaka promocja zadziałała,
         * - ile wyniósł rabat,
         * - jaki był powód rabatu.
         */
        List<PromotionDtos.PromotionAdjustment> adjustments = new ArrayList<>();

        /*
         * Łączna wartość rabatów.
         *
         * Na końcu odejmujemy ją od subtotalu.
         */
        BigDecimal discount = BigDecimal.ZERO;

        /*
         * Naliczanie promocji automatycznych.
         *
         * Promocje są sprawdzane w kolejności priority.
         */
        for (Promotion promotion : activePromotions) {
            BigDecimal adjustment = calculatePromotionAdjustment(promotion, request);

            /*
             * Promocja jest uwzględniana tylko wtedy, gdy faktycznie daje rabat.
             *
             * adjustment <= 0 ignorujemy, bo nie zmienia ceny.
             */
            if (adjustment.signum() > 0) {
                adjustments.add(
                        new PromotionDtos.PromotionAdjustment(
                                promotion.getName(),
                                adjustment,
                                promotion.getType().name()
                        )
                );

                discount = discount.add(adjustment);

                /*
                 * Jeśli promocja nie jest stackable, kończymy naliczanie kolejnych
                 * automatycznych promocji.
                 *
                 * To chroni marżę i pozwala wymusić zasadę:
                 * "ta promocja nie łączy się z innymi".
                 */
                if (!promotion.isStackable()) {
                    break;
                }
            }
        }

        /*
         * Obsługa kuponu.
         *
         * Kupon jest opcjonalny i naliczany po promocjach automatycznych.
         */
        if (request.couponCode() != null && !request.couponCode().isBlank()) {
            Coupon coupon = coupons.findByCodeIgnoreCase(request.couponCode())
                    .orElseThrow(() -> ApiException.notFound("Coupon not found"));

            /*
             * Sprawdzamy:
             * - czy kupon jest aktywny,
             * - czy nie przekroczył limitu użyć,
             * - czy promocja powiązana z kuponem jest aktywna czasowo.
             *
             * Jeśli kupon nie może zostać użyty, zwracamy błąd requestu.
             */
            if (!coupon.canUse() || !coupon.getPromotion().isActiveAt(Instant.now())) {
                throw ApiException.badRequest("Coupon is not active");
            }

            /*
             * Kupon sam nie ma logiki rabatu.
             * Logika pochodzi z promocji powiązanej z kuponem.
             */
            BigDecimal couponAdjustment = calculatePromotionAdjustment(
                    coupon.getPromotion(),
                    request
            );

            if (couponAdjustment.signum() > 0) {
                adjustments.add(
                        new PromotionDtos.PromotionAdjustment(
                                "Coupon " + coupon.getCode(),
                                couponAdjustment,
                                coupon.getPromotion().getType().name()
                        )
                );

                discount = discount.add(couponAdjustment);
            }
        }

        /*
         * Finalny total.
         *
         * Wzór:
         * total = subtotal - discount + shipping
         *
         * Rabaty nie mogą obniżyć totalu poniżej zera.
         */
        BigDecimal total = subtotal.subtract(discount).add(shipping);

        if (total.signum() < 0) {
            total = BigDecimal.ZERO;
        }

        /*
         * Zwracamy pełne rozbicie ceny.
         *
         * To jest ważne dla UI i debugowania:
         * - subtotal,
         * - discountAmount,
         * - shippingAmount,
         * - total,
         * - lista zastosowanych adjustmentów.
         */
        return new PromotionDtos.PriceResponse(
                subtotal,
                discount,
                shipping,
                total.setScale(2, RoundingMode.HALF_UP),
                adjustments
        );
    }

    /**
     * Wylicza rabat dla pojedynczej promocji.
     *
     * Każdy typ promocji ma inną logikę:
     * - PERCENT_OFF — procent od całego koszyka,
     * - AMOUNT_OFF — stała kwota rabatu,
     * - FREE_SHIPPING — rabat równy kosztowi dostawy,
     * - CATEGORY_PERCENT_OFF — procent od pozycji z danej kategorii,
     * - PRODUCT_PERCENT_OFF — procent od pozycji z danego produktu,
     * - BUY_X_GET_Y — gratisowe sztuki w grupach zakupowych.
     */
    private BigDecimal calculatePromotionAdjustment(
            Promotion promotion,
            PromotionDtos.PriceRequest request
    ) {
        return switch (promotion.getType()) {
            /*
             * Procentowy rabat od całego subtotalu produktów.
             */
            case PERCENT_OFF -> percentOff(
                    totalLines(request.lines()),
                    promotion.getValue()
            );

            /*
             * Stała kwota rabatu.
             *
             * Uwaga:
             * Główna metoda price() zabezpiecza total przed zejściem poniżej 0.
             */
            case AMOUNT_OFF -> promotion.getValue();

            /*
             * Darmowa dostawa.
             *
             * Rabat jest równy shippingAmount.
             * Jeśli shippingAmount nie podano, rabat wynosi 0.
             */
            case FREE_SHIPPING -> request.shippingAmount() == null
                    ? BigDecimal.ZERO
                    : request.shippingAmount();

            /*
             * Rabat procentowy tylko na pozycje z konkretnej kategorii.
             *
             * Jeśli promocja nie ma categoryId albo żadna linia nie pasuje,
             * totalLines zwróci 0 i rabat nie zostanie naliczony.
             */
            case CATEGORY_PERCENT_OFF -> percentOff(
                    totalLines(
                            request.lines()
                                    .stream()
                                    .filter(line ->
                                            promotion.getCategoryId() != null
                                                    && promotion.getCategoryId().equals(line.categoryId())
                                    )
                                    .toList()
                    ),
                    promotion.getValue()
            );

            /*
             * Rabat procentowy tylko na konkretny produkt.
             */
            case PRODUCT_PERCENT_OFF -> percentOff(
                    totalLines(
                            request.lines()
                                    .stream()
                                    .filter(line ->
                                            promotion.getProductId() != null
                                                    && promotion.getProductId().equals(line.productId())
                                    )
                                    .toList()
                    ),
                    promotion.getValue()
            );

            /*
             * Promocja typu kup X, dostań Y.
             *
             * Rabat jest liczony jako wartość gratisowych sztuk.
             */
            case BUY_X_GET_Y -> buyXGetY(request.lines(), promotion);
        };
    }

    /**
     * Sumuje wartość przekazanych linii koszyka.
     *
     * Dla każdej pozycji:
     * unitPrice * quantity
     */
    private BigDecimal totalLines(List<PromotionDtos.PriceLine> lines) {
        return lines.stream()
                .map(line -> line.unitPrice().multiply(BigDecimal.valueOf(line.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Liczy rabat procentowy od kwoty.
     *
     * Przykład:
     * amount = 200
     * percent = 10
     * wynik = 20.00
     *
     * Wynik zaokrąglamy do 2 miejsc po przecinku.
     */
    private BigDecimal percentOff(BigDecimal amount, BigDecimal percent) {
        return amount.multiply(percent)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    /**
     * Liczy rabat dla promocji BUY_X_GET_Y.
     *
     * Przykład:
     * buyQuantity = 2
     * freeQuantity = 1
     *
     * Przy quantity = 6:
     * group = 3
     * freeItems = 6 / 3 * 1 = 2
     *
     * Rabat = 2 * unitPrice.
     *
     * Uwaga:
     * Obecna implementacja liczy promocję per linia koszyka.
     * Nie miesza różnych produktów ani wariantów w jednej grupie.
     */
    private BigDecimal buyXGetY(
            List<PromotionDtos.PriceLine> lines,
            Promotion promotion
    ) {
        /*
         * Jeśli promocja nie ma pełnej konfiguracji X/Y,
         * nie naliczamy rabatu.
         */
        if (promotion.getBuyQuantity() == null || promotion.getFreeQuantity() == null) {
            return BigDecimal.ZERO;
        }

        return lines.stream()
                .map(line -> {
                    /*
                     * Grupa zakupowa:
                     * buyQuantity + freeQuantity.
                     *
                     * Dla "kup 2, trzeci gratis":
                     * group = 2 + 1 = 3.
                     */
                    int group = promotion.getBuyQuantity() + promotion.getFreeQuantity();

                    /*
                     * Niepoprawna konfiguracja promocji.
                     *
                     * Zamiast rzucać wyjątek w runtime pricingu,
                     * zwracamy 0, żeby zła reguła nie rozwaliła checkoutu.
                     *
                     * Docelowo taka walidacja powinna być w PromotionAdminService.
                     */
                    if (group <= 0) {
                        return BigDecimal.ZERO;
                    }

                    /*
                     * Liczba darmowych sztuk w tej linii.
                     *
                     * Przykład:
                     * quantity = 7
                     * buyQuantity = 2
                     * freeQuantity = 1
                     *
                     * quantity / group = 7 / 3 = 2 pełne grupy
                     * freeItems = 2 * 1 = 2
                     */
                    int freeItems = (line.quantity() / group) * promotion.getFreeQuantity();

                    /*
                     * Rabat to wartość darmowych sztuk.
                     */
                    return line.unitPrice()
                            .multiply(BigDecimal.valueOf(freeItems));
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}