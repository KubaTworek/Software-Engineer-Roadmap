package pl.jakubtworek.marketplace.inventory.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.marketplace.inventory.domain.StockItem;

import java.util.UUID;

/**
 * Use case odpowiedzialny za dodanie stocku dla produktu.
 *
 * Ta klasa należy do warstwy aplikacyjnej modułu Inventory.
 * Jej zadaniem jest orkiestracja operacji magazynowej:
 * - znalezienie istniejącego stanu magazynowego produktu,
 * - utworzenie nowego wpisu magazynowego, jeśli jeszcze nie istnieje,
 * - wykonanie operacji domenowej na agregacie StockItem,
 * - zapisanie zmienionego agregatu przez port repozytorium.
 *
 * Use case nie powinien znać szczegółów HTTP, SQL, JPA ani Kafki.
 */
@Service
public class AddStockUseCase {

    /**
     * Port repozytorium stocku.
     *
     * Use case zależy od abstrakcji z warstwy aplikacyjnej, a nie od konkretnej
     * implementacji infrastrukturalnej. Dzięki temu zapis może być realizowany
     * przez repozytorium in-memory, JDBC, JPA albo inny adapter.
     */
    private final StockRepository repository;

    public AddStockUseCase(StockRepository repository) {
        this.repository = repository;
    }

    /**
     * Dodaje wskazaną ilość produktu do dostępnego stocku.
     *
     * Granica transakcji znajduje się na poziomie use case’a, ponieważ cała operacja
     * powinna być wykonana atomowo:
     * - odczyt aktualnego stanu,
     * - zmiana agregatu,
     * - zapis nowego stanu.
     *
     * Jeśli stock dla danego produktu jeszcze nie istnieje, tworzymy nowy agregat
     * z ilością początkową równą 0, a następnie dodajemy żądaną ilość.
     */
    @Transactional
    public void handle(UUID productId, int quantity) {
        StockItem item = repository.findByProductId(productId)
                .orElseGet(() -> StockItem.create(productId, 0));

        item.add(quantity);

        repository.save(item);
    }
}