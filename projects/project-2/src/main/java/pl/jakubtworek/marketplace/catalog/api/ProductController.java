package pl.jakubtworek.marketplace.catalog.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pl.jakubtworek.marketplace.catalog.application.CreateProductUseCase;
import pl.jakubtworek.marketplace.catalog.application.ProductRepository;
import pl.jakubtworek.marketplace.catalog.domain.ProductId;

import java.util.UUID;

/**
 * Adapter HTTP dla modułu Catalog.
 *
 * Ten kontroler należy do warstwy API/infrastruktury. Jego odpowiedzialnością jest
 * przetłumaczenie żądania HTTP na komendę warstwy aplikacyjnej oraz przetłumaczenie
 * wyniku działania aplikacji/domeny na odpowiedź HTTP.
 *
 * Ważna zasada architektoniczna:
 * - kontroler może zależeć od warstwy aplikacyjnej,
 * - kontroler nie powinien zawierać reguł biznesowych,
 * - kontroler nie powinien wystawiać obiektów domenowych bezpośrednio jako odpowiedzi API.
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    /**
     * Use case odpowiedzialny za utworzenie produktu.
     *
     * Kontroler deleguje wykonanie operacji biznesowej do warstwy aplikacyjnej,
     * zamiast samodzielnie tworzyć lub modyfikować obiekty domenowe.
     */
    private final CreateProductUseCase createProduct;

    /**
     * Repozytorium używane tutaj wyłącznie do prostego endpointu odczytowego.
     *
     * W bardziej dojrzałej wersji projektu można byłoby zastąpić je dedykowanym
     * query service albo osobnym modelem odczytowym, żeby API nie korzystało
     * bezpośrednio z repozytorium strony zapisu.
     */
    private final ProductRepository repository;

    public ProductController(CreateProductUseCase createProduct, ProductRepository repository) {
        this.createProduct = createProduct;
        this.repository = repository;
    }

    /**
     * Tworzy nowy produkt w katalogu.
     *
     * Adnotacja @Valid powoduje walidację ciała żądania przed wejściem do metody.
     * DTO z warstwy API jest tłumaczone na komendę use case’a.
     *
     * Metoda zwraca HTTP 201 Created oraz identyfikator utworzonego produktu.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IdResponse create(@Valid @RequestBody CreateProductRequest request) {
        ProductId id = createProduct.handle(
                new CreateProductUseCase.Command(
                        request.name(),
                        request.amount(),
                        request.currency()
                )
        );

        return new IdResponse(id.value());
    }

    /**
     * Zwraca szczegóły produktu po identyfikatorze.
     *
     * UUID z adresu URL jest zamieniany na domenowy value object ProductId.
     *
     * Obecne ograniczenie:
     * - orElseThrow() bez dedykowanego wyjątku może skończyć się nieczytelnym błędem HTTP.
     *
     * Lepsze rozwiązanie produkcyjne:
     * - rzucić dedykowany ProductNotFoundException,
     * - zmapować go na HTTP 404 Not Found w @ControllerAdvice.
     */
    @GetMapping("/{id}")
    public ProductResponse get(@PathVariable UUID id) {
        var product = repository.findById(ProductId.of(id))
                .orElseThrow();

        return new ProductResponse(
                product.id().value(),
                product.name(),
                product.price().amount().toPlainString(),
                product.price().currency().getCurrencyCode(),
                product.status().name()
        );
    }

    /**
     * DTO żądania utworzenia produktu.
     *
     * Ten typ należy do warstwy API i nie powinien być używany jako obiekt domenowy.
     *
     * Walidacja w tym miejscu sprawdza tylko podstawową poprawność danych wejściowych HTTP.
     * Głębsze reguły biznesowe powinny nadal znajdować się w domenie albo warstwie aplikacyjnej.
     */
    public record CreateProductRequest(
            @NotBlank String name,
            @NotBlank String amount,
            @NotBlank String currency
    ) {
    }

    /**
     * Prosta odpowiedź zawierająca identyfikator nowo utworzonego zasobu.
     */
    public record IdResponse(UUID id) {
    }

    /**
     * DTO odpowiedzi wystawiane przez API modułu Catalog.
     *
     * Celowo spłaszczamy tutaj obiekty domenowe, takie jak Money, do prostych pól tekstowych.
     * Dzięki temu nie ujawniamy klientom API wewnętrznej struktury modelu domenowego.
     */
    public record ProductResponse(
            UUID id,
            String name,
            String amount,
            String currency,
            String status
    ) {
    }
}