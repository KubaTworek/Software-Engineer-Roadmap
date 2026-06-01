package pl.jakubtworek.marketplace.inventory.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.marketplace.inventory.domain.StockItem;

import java.util.UUID;

@Service
public class AddStockUseCase {
    private final StockRepository repository;

    public AddStockUseCase(StockRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void handle(UUID productId, int quantity) {
        StockItem item = repository.findByProductId(productId).orElseGet(() -> StockItem.create(productId, 0));
        item.add(quantity);
        repository.save(item);
    }
}
