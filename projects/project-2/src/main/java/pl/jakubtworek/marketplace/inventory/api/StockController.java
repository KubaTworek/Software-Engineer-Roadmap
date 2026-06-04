package pl.jakubtworek.marketplace.inventory.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pl.jakubtworek.marketplace.inventory.application.AddStockUseCase;
import pl.jakubtworek.marketplace.inventory.application.StockRepository;

import java.util.UUID;

/**
 * Adapter HTTP dla modułu Inventory.
 *
 * Ten kontroler odpowiada za wystawienie operacji magazynowych przez REST API.
 * Jego zadaniem jest przyjęcie żądania HTTP, wykonanie podstawowej walidacji wejścia,
 * przekazanie komendy do warstwy aplikacyjnej oraz zmapowanie wyniku na odpowiedź HTTP.
 *
 * Ważne:
 * - kontroler nie powinien zawierać reguł biznesowych,
 * - kontroler nie powinien bezpośrednio modyfikować agregatów,
 * - kontroler powinien delegować operacje do use case’ów,
 * - model domenowy nie powinien zależeć od tej klasy.
 */
@RestController
@RequestMapping("/api/stock")
public class StockController {

    /**
     * Use case odpowiedzialny za dodanie dostępnego stocku dla produktu.
     *
     * Kontroler nie wykonuje logiki magazynowej samodzielnie.
     * Przekazuje żądanie do warstwy aplikacyjnej, która orkiestruje operację.
     */
    private final AddStockUseCase addStock;

    /**
     * Repozytorium używane tutaj do prostego endpointu odczytowego.
     *
     * W bardziej rozwiniętym projekcie można byłoby zastąpić to dedykowanym
     * query service albo osobnym modelem odczytowym. Dzięki temu API nie musiałoby
     * korzystać bezpośrednio z repozytorium agregatu.
     */
    private final StockRepository repository;

    public StockController(AddStockUseCase addStock, StockRepository repository) {
        this.addStock = addStock;
        this.repository = repository;
    }

    /**
     * Dodaje ilość produktu do magazynu.
     *
     * Przykładowe zastosowanie:
     * - przyjęcie dostawy,
     * - ręczna korekta stocku,
     * - przygotowanie danych testowych.
     *
     * Adnotacja @Valid uruchamia walidację DTO wejściowego.
     *
     * Zwracamy HTTP 204 No Content, ponieważ operacja nie musi zwracać ciała odpowiedzi.
     * W praktyce można też rozważyć HTTP 200 z aktualnym stanem stocku, ale 204 jest
     * wystarczające dla prostej komendy.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void add(@Valid @RequestBody AddStockRequest request) {
        addStock.handle(request.productId(), request.quantity());
    }

    /**
     * Zwraca stan magazynowy dla konkretnego produktu.
     *
     * UUID z adresu URL jest używany jako identyfikator produktu w module Inventory.
     *
     * Obecne ograniczenie:
     * - orElseThrow() bez dedykowanego wyjątku może skutkować nieczytelnym błędem HTTP.
     *
     * Lepsze rozwiązanie:
     * - rzucić StockItemNotFoundException,
     * - zmapować go na HTTP 404 Not Found w @ControllerAdvice.
     */
    @GetMapping("/{productId}")
    public StockResponse get(@PathVariable UUID productId) {
        var item = repository.findByProductId(productId)
                .orElseThrow();

        return new StockResponse(
                item.productId(),
                item.availableQuantity(),
                item.reservedQuantity()
        );
    }

    /**
     * DTO żądania dodania stocku.
     *
     * Ten typ należy do warstwy API i nie powinien być używany jako obiekt domenowy.
     *
     * Walidacja tutaj dotyczy tylko podstawowej poprawności wejścia HTTP.
     * Reguły biznesowe, np. czy można dodać stock dla danego produktu, powinny
     * znajdować się w warstwie aplikacyjnej albo domenowej.
     */
    public record AddStockRequest(
            @NotNull UUID productId,

            /**
             * Ilość dodawana do dostępnego stocku.
             *
             * Wartość powinna być dodatnia. Użycie @Min(1) blokuje przypadki:
             * - quantity = 0,
             * - quantity < 0.
             */
            @Min(1) int quantity
    ) {
    }

    /**
     * DTO odpowiedzi z aktualnym stanem magazynowym produktu.
     *
     * availableQuantity oznacza ilość możliwą jeszcze do zarezerwowania.
     * reservedQuantity oznacza ilość już zarezerwowaną przez zamówienia,
     * ale jeszcze niekoniecznie zdjętą finalnie ze stocku.
     */
    public record StockResponse(
            UUID productId,
            int availableQuantity,
            int reservedQuantity
    ) {
    }
}