package pl.jakubtworek.marketplace.inventory.infrastructure;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import pl.jakubtworek.marketplace.inventory.application.StockRepository;
import pl.jakubtworek.marketplace.inventory.domain.StockItem;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementacja portu StockRepository.
 *
 * Ta klasa należy do warstwy infrastruktury modułu Inventory.
 * Implementuje port z warstwy aplikacyjnej, ale sama nie powinna być znana domenie.
 *
 * Taki adapter jest przydatny na wczesnym etapie projektu:
 * - pozwala rozwijać domenę bez bazy danych,
 * - ułatwia testowanie use case’ów,
 * - upraszcza lokalne uruchamianie aplikacji.
 *
 * Nie jest to implementacja produkcyjna.
 * Dane są trzymane wyłącznie w pamięci procesu i znikają po restarcie aplikacji.
 */
@Repository
@Profile("!postgres")
public class InMemoryStockRepository implements StockRepository {

    /**
     * Prosty magazyn danych w pamięci.
     *
     * Kluczem jest productId, ponieważ w tym modelu jeden produkt ma jeden wpis magazynowy.
     *
     * ConcurrentHashMap ogranicza problemy przy równoległym dostępie do mapy,
     * ale nie zapewnia pełnej transakcyjności ani ochrony przed race condition
     * na poziomie samego agregatu StockItem.
     */
    private final Map<UUID, StockItem> items = new ConcurrentHashMap<>();

    /**
     * Zapisuje stan magazynowy produktu.
     *
     * Jeśli wpis dla danego productId już istnieje, zostanie nadpisany.
     *
     * Warto pamiętać, że ta implementacja przechowuje referencję do obiektu StockItem.
     * Oznacza to, że zmiana pobranego obiektu może być widoczna w mapie nawet bez
     * ponownego wywołania save(...). Baza danych zwykle tak się nie zachowuje,
     * dlatego testy integracyjne powinny później sprawdzać adapter JDBC/JPA osobno.
     */
    @Override
    public StockItem save(StockItem item) {
        items.put(item.productId(), item);
        return item;
    }

    /**
     * Wyszukuje stan magazynowy po identyfikatorze produktu.
     *
     * Zwracamy Optional, ponieważ stock dla danego produktu może jeszcze nie istnieć.
     * Taka sytuacja jest normalna np. przed pierwszym przyjęciem towaru do magazynu.
     */
    @Override
    public Optional<StockItem> findByProductId(UUID productId) {
        return Optional.ofNullable(items.get(productId));
    }
}