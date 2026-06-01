package pl.jakubtworek.marketplace.inventory.application;

import pl.jakubtworek.marketplace.inventory.domain.StockItem;

import java.util.Optional;
import java.util.UUID;

public interface StockRepository {
    StockItem save(StockItem item);
    Optional<StockItem> findByProductId(UUID productId);
}
