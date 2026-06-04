package pl.jakubtworek.marketplace.catalog.infrastructure;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import pl.jakubtworek.marketplace.catalog.application.ProductRepository;
import pl.jakubtworek.marketplace.catalog.domain.Product;
import pl.jakubtworek.marketplace.catalog.domain.ProductId;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementacja portu ProductRepository.
 *
 * Ta klasa należy do warstwy infrastruktury. Implementuje port z warstwy aplikacyjnej,
 * ale sama nie powinna być używana bezpośrednio przez domenę.
 *
 * W modularnym monolicie taka implementacja jest przydatna na początku projektu:
 * - pozwala szybko testować use case’y,
 * - nie wymaga bazy danych,
 * - upraszcza pierwszą fazę pracy nad domeną.
 *
 * Nie jest to jednak implementacja produkcyjna.
 * Po dodaniu trwałości danych powinna zostać zastąpiona np. adapterem JDBC albo JPA.
 */
@Profile("!postgres")
@Repository
public class InMemoryProductRepository implements ProductRepository {

    /**
     * Prosty magazyn danych w pamięci procesu.
     *
     * Kluczem jest domenowy ProductId, a wartością agregat Product.
     *
     * ConcurrentHashMap pozwala bezpieczniej obsługiwać równoległy dostęp niż zwykły HashMap,
     * ale nie rozwiązuje wszystkich problemów transakcyjności i spójności danych.
     */
    private final Map<ProductId, Product> products = new ConcurrentHashMap<>();

    /**
     * Zapisuje produkt w pamięci.
     *
     * Jeśli produkt o tym samym ID już istnieje, zostanie nadpisany.
     * To zachowanie jest wystarczające dla prostego adaptera in-memory, ale w bazie danych
     * warto świadomie rozróżnić operacje insert/update albo stosować optimistic locking.
     */
    @Override
    public Product save(Product product) {
        products.put(product.id(), product);
        return product;
    }

    /**
     * Wyszukuje produkt po identyfikatorze.
     *
     * Zwracamy Optional, ponieważ produkt o podanym ID może nie istnieć.
     * Dzięki temu warstwa aplikacyjna/API musi jawnie obsłużyć przypadek braku produktu.
     */
    @Override
    public Optional<Product> findById(ProductId id) {
        return Optional.ofNullable(products.get(id));
    }
}