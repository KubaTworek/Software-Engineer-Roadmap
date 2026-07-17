package com.example.ecommerce.promotion;

import com.example.ecommerce.common.ApiException;
import com.example.ecommerce.promotion.dto.PromotionDtos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Serwis adminowy do tworzenia promocji i kuponów.
 *
 * Ta klasa nie liczy rabatów dla koszyka.
 * Od tego jest PromotionEngine.
 *
 * PromotionAdminService odpowiada za konfigurację promocji:
 * - utworzenie reguły promocyjnej,
 * - przypięcie promocji do produktu albo kategorii,
 * - ustawienie parametrów BUY_X_GET_Y,
 * - ustawienie priorytetu,
 * - ustawienie stackowania promocji,
 * - utworzenie kuponu powiązanego z promocją.
 *
 * To jest warstwa używana przez panel admina.
 */
@Service
public class PromotionAdminService {

    /**
     * Repozytorium promocji.
     *
     * Przechowuje główne reguły promocyjne, np.:
     * - 10% na cały koszyk,
     * - 20% na kategorię,
     * - darmowa dostawa,
     * - kup 2, trzeci gratis.
     */
    private final PromotionRepository promotions;

    /**
     * Repozytorium kuponów.
     *
     * Kupon jest kodem wpisywanym przez użytkownika,
     * ale sama logika rabatu nadal pochodzi z powiązanej promocji.
     *
     * Przykład:
     * kod SUMMER20 wskazuje na promocję 20% zniżki.
     */
    private final CouponRepository coupons;

    /**
     * Constructor injection.
     *
     * Serwis potrzebuje repozytoriów promocji i kuponów.
     */
    public PromotionAdminService(
            PromotionRepository promotions,
            CouponRepository coupons
    ) {
        this.promotions = promotions;
        this.coupons = coupons;
    }

    /**
     * Tworzy nową promocję.
     *
     * Flow:
     * 1. Tworzy encję Promotion z podstawowych danych.
     * 2. Opcjonalnie ustawia target kategorii.
     * 3. Opcjonalnie ustawia target produktu.
     * 4. Opcjonalnie ustawia parametry BUY_X_GET_Y.
     * 5. Opcjonalnie ustawia priorytet.
     * 6. Opcjonalnie ustawia, czy promocja może się stackować z innymi.
     * 7. Zapisuje promocję i zwraca jej ID.
     *
     * @Transactional:
     * cała konfiguracja promocji zapisuje się atomowo.
     */
    @Transactional
    public Long createPromotion(PromotionDtos.CreatePromotionRequest request) {
        /*
         * Tworzymy bazową promocję.
         *
         * Dane podstawowe:
         * - name — nazwa widoczna w adminie,
         * - type — typ promocji,
         * - value — wartość rabatu, np. procent albo kwota,
         * - startsAt/endsAt — okno czasowe działania.
         */
        Promotion promotion = new Promotion(
                request.name(),
                request.type(),
                request.value(),
                request.startsAt(),
                request.endsAt()
        );

        /*
         * Target kategorii.
         *
         * Używane np. dla promocji:
         * - 20% na kategorię "Buty",
         * - darmowa dostawa dla konkretnej kategorii,
         * - sezonowa promocja na elektronikę.
         */
        if (request.categoryId() != null) {
            promotion.setTargetCategory(request.categoryId());
        }

        /*
         * Target produktu.
         *
         * Używane dla promocji przypisanej do konkretnego produktu,
         * np. rabat tylko na jeden model telefonu.
         */
        if (request.productId() != null) {
            promotion.setTargetProduct(request.productId());
        }

        /*
         * Parametry promocji BUY_X_GET_Y.
         *
         * Przykład:
         * buyQuantity = 2
         * freeQuantity = 1
         *
         * Oznacza: kup 2, dostań 1 gratis.
         *
         * Uwaga:
         * W produkcji warto zwalidować, że oba pola są podane razem
         * i że mają wartości większe od zera.
         */
        if (request.buyQuantity() != null || request.freeQuantity() != null) {
            promotion.setBuyXGetY(
                    request.buyQuantity(),
                    request.freeQuantity()
            );
        }

        /*
         * Priorytet promocji.
         *
         * PromotionEngine może używać priorytetu do ustalenia,
         * która promocja ma zostać zastosowana jako pierwsza.
         *
         * Niższa wartość zwykle oznacza wyższy priorytet.
         */
        if (request.priority() != null) {
            promotion.setPriority(request.priority());
        }

        /*
         * Stackowanie promocji.
         *
         * Jeśli stackable = true, promocja może działać razem z innymi promocjami.
         * Jeśli false, silnik promocji może przerwać naliczanie po jej zastosowaniu.
         *
         * To ważne, żeby kontrolować rentowność rabatów.
         */
        if (request.stackable() != null) {
            promotion.setStackable(request.stackable());
        }

        /*
         * Zapisujemy promocję i zwracamy jej ID.
         *
         * Admin panel może użyć tego ID np. do utworzenia kuponu,
         * edycji promocji albo podglądu konfiguracji.
         */
        return promotions.save(promotion).getId();
    }

    /**
     * Tworzy kupon powiązany z istniejącą promocją.
     *
     * Kupon sam nie definiuje logiki rabatu.
     * Kupon wskazuje na Promotion, która określa typ i wartość zniżki.
     *
     * Flow:
     * 1. Pobierz promocję po promotionId.
     * 2. Jeśli promocja nie istnieje, zwróć 404.
     * 3. Utwórz Coupon z kodem, limitem użyć i limitem per user.
     * 4. Zapisz kupon.
     * 5. Zwróć ID kuponu.
     */
    @Transactional
    public Long createCoupon(PromotionDtos.CreateCouponRequest request) {
        /*
         * Kupon musi być powiązany z istniejącą promocją.
         *
         * Nie tworzymy kuponu "wiszącego", bo PromotionEngine nie wiedziałby,
         * jaki rabat ma naliczyć.
         */
        Promotion promotion = promotions.findById(request.promotionId())
                .orElseThrow(() -> ApiException.notFound("Promotion not found"));

        /*
         * Tworzymy kupon.
         *
         * code — kod wpisywany przez klienta,
         * maxUses — globalny limit użyć kuponu,
         * userLimit — limit użyć przez jednego użytkownika.
         *
         * Uwaga:
         * W obecnej wersji userLimit jest zapisany w modelu,
         * ale pełne egzekwowanie limitu per user wymaga historii użyć kuponów.
         */
        return coupons.save(
                new Coupon(
                        request.code(),
                        promotion,
                        request.maxUses(),
                        request.userLimit()
                )
        ).getId();
    }
}