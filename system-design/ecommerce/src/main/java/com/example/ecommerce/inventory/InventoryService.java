package com.example.ecommerce.inventory;

import com.example.ecommerce.catalog.ProductVariant;
import com.example.ecommerce.common.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class InventoryService {
    private final InventoryItemRepository items;
    private final InventoryReservationRepository reservations;

    public InventoryService(InventoryItemRepository items, InventoryReservationRepository reservations) {
        this.items = items;
        this.reservations = reservations;
    }

    @Transactional
    public void createInventoryItem(ProductVariant variant, int initialStock) {
        items.save(new InventoryItem(variant, Math.max(0, initialStock)));
    }

    public int availableQuantity(Long variantId) {
        return items.findByVariantId(variantId)
                .map(InventoryItem::sellableQuantity)
                .orElse(0);
    }

    @Transactional
    public void updateStock(Long variantId, int availableQuantity) {
        InventoryItem item = items.findByVariantId(variantId)
                .orElseThrow(() -> ApiException.notFound("Inventory item not found"));
        item.setAvailableQuantity(Math.max(0, availableQuantity));
    }

    @Transactional
    public void reserve(Long orderId, ProductVariant variant, int quantity) {
        InventoryItem item = items.findByVariantId(variant.getId())
                .orElseThrow(() -> ApiException.notFound("Inventory item not found"));

        if (item.sellableQuantity() < quantity) {
            throw ApiException.conflict("Not enough stock for variant " + variant.getId());
        }

        item.reserve(quantity);
        reservations.save(new InventoryReservation(
                orderId,
                variant,
                quantity,
                Instant.now().plus(15, ChronoUnit.MINUTES)
        ));
    }

    @Transactional
    public void confirmReservations(Long orderId) {
        var activeReservations = reservations.findByOrderIdAndStatus(orderId, ReservationStatus.ACTIVE);

        for (InventoryReservation reservation : activeReservations) {
            InventoryItem item = items.findByVariantId(reservation.getVariant().getId())
                    .orElseThrow(() -> ApiException.notFound("Inventory item not found"));

            item.confirmSale(reservation.getQuantity());
            reservation.confirm();
        }
    }

    @Transactional
    public void releaseReservations(Long orderId) {
        var activeReservations = reservations.findByOrderIdAndStatus(orderId, ReservationStatus.ACTIVE);

        for (InventoryReservation reservation : activeReservations) {
            InventoryItem item = items.findByVariantId(reservation.getVariant().getId())
                    .orElseThrow(() -> ApiException.notFound("Inventory item not found"));

            item.release(reservation.getQuantity());
            reservation.release();
        }
    }
}
