package pl.jakubtworek.marketplace.inventory.infrastructure;

import org.springframework.stereotype.Repository;
import pl.jakubtworek.marketplace.inventory.application.StockRepository;
import pl.jakubtworek.marketplace.inventory.domain.StockItem;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryStockRepository implements StockRepository {
    private final Map<UUID, StockItem> items = new ConcurrentHashMap<>();

    @Override
    public StockItem save(StockItem item) {
        items.put(item.productId(), item);
        return item;
    }

    @Override
    public Optional<StockItem> findByProductId(UUID productId) {
        return Optional.ofNullable(items.get(productId));
    }
}
