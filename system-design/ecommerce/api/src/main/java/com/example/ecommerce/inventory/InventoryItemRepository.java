package com.example.ecommerce.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface InventoryItemRepository extends JpaRepository<InventoryItem, Long> {
    Optional<InventoryItem> findByVariantId(Long variantId);

    /**
     * Serializuje decyzję check-and-reserve dla jednego SKU. Bez tej blokady
     * dwa checkouty mogłyby odczytać tę samą wartość sellableQuantity.
     * Blokada obejmuje wyłącznie czas lokalnej transakcji bazodanowej.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from InventoryItem i where i.variant.id = :variantId")
    Optional<InventoryItem> findByVariantIdForUpdate(Long variantId);
}
