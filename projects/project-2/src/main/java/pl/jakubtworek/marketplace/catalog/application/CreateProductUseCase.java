package pl.jakubtworek.marketplace.catalog.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.marketplace.catalog.domain.Product;
import pl.jakubtworek.marketplace.catalog.domain.ProductId;
import pl.jakubtworek.marketplace.shared.kernel.Money;

/**
 * Use case odpowiedzialny za utworzenie produktu w katalogu.
 *
 * Ta klasa należy do warstwy aplikacyjnej. Jej zadaniem jest orkiestracja operacji:
 * - przyjęcie komendy,
 * - utworzenie obiektu domenowego,
 * - zapis przez port repozytorium,
 * - zwrócenie wyniku do warstwy API.
 *
 * Nie powinna zawierać szczegółów HTTP, JPA, SQL ani logiki frameworkowej poza minimalną
 * konfiguracją aplikacyjną, np. transakcją.
 */
@Service
public class CreateProductUseCase {

    /**
     * Port repozytorium produktu.
     *
     * Use case zależy od abstrakcji, a nie od konkretnej implementacji bazy danych.
     * Dzięki temu implementacja repozytorium może być in-memory, JDBC, JPA albo inna,
     * bez zmiany logiki aplikacyjnej.
     */
    private final ProductRepository repository;

    public CreateProductUseCase(ProductRepository repository) {
        this.repository = repository;
    }

    /**
     * Obsługuje komendę utworzenia produktu.
     *
     * Granica transakcji znajduje się na poziomie use case’a, ponieważ to use case
     * reprezentuje pojedynczą operację aplikacyjną.
     *
     * Przepływ:
     * 1. dane wejściowe z komendy są zamieniane na obiekty domenowe,
     * 2. domenowa fabryka Product.create(...) tworzy produkt i pilnuje podstawowych reguł,
     * 3. produkt zostaje zapisany przez port repozytorium,
     * 4. zwracany jest identyfikator utworzonego produktu.
     */
    @Transactional
    public ProductId handle(Command command) {
        Product product = Product.create(
                command.name(),
                Money.of(command.amount(), command.currency())
        );

        return repository.save(product).id();
    }

    /**
     * Komenda wejściowa use case’a.
     *
     * To nie jest DTO HTTP, mimo że może mieć podobne pola.
     * Komenda należy do warstwy aplikacyjnej i opisuje intencję wykonania operacji:
     * "utwórz produkt o podanej nazwie i cenie".
     *
     * W prostym projekcie pola mogą być typu String, ale docelowo można rozważyć
     * wcześniejszą walidację/parsowanie kwoty i waluty, żeby ograniczyć możliwość
     * przekazania błędnych danych głębiej do domeny.
     */
    public record Command(
            String name,
            String amount,
            String currency
    ) {
    }
}