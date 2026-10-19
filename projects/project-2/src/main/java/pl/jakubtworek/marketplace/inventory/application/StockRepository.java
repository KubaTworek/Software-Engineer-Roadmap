package pl.jakubtworek.marketplace.inventory.application;

import pl.jakubtworek.marketplace.inventory.domain.StockItem;

import java.util.Optional;
import java.util.UUID;

public interface StockRepository {
    StockItem save(StockItem item);
    Optional<StockItem> findByProductId(UUID productId);

    /**
     * Pobiera wiersz z blokadą do końca lokalnej transakcji. Adapter pamięciowy
     * zachowuje się jak zwykły odczyt, ale PostgreSQL używa SELECT ... FOR UPDATE.
     * To repozytorium, a nie sam mutable agregat, wyznacza granicę współbieżności.
     */
    default Optional<StockItem> findByProductIdForUpdate(UUID productId) {
        return findByProductId(productId);
    }
}
