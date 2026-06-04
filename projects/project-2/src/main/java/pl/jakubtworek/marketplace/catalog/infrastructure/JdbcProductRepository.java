package pl.jakubtworek.marketplace.catalog.infrastructure;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.marketplace.catalog.application.ProductRepository;
import pl.jakubtworek.marketplace.catalog.domain.Product;
import pl.jakubtworek.marketplace.catalog.domain.ProductId;
import pl.jakubtworek.marketplace.catalog.domain.ProductStatus;
import pl.jakubtworek.marketplace.shared.kernel.Money;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC implementacja portu ProductRepository.
 *
 * To jest adapter infrastrukturalny dla modułu Catalog.
 * Warstwa aplikacyjna zna tylko interfejs ProductRepository, a nie szczegóły JDBC,
 * SQL-a ani struktury tabeli.
 *
 * Ten adapter odpowiada za:
 * - zapis agregatu Product do tabeli catalog.products,
 * - odczyt rekordu z bazy,
 * - odtworzenie obiektu domenowego Product przez Product.restore(...).
 *
 * Domena nadal pozostaje czysta:
 * - Product nie zna JDBC,
 * - Product nie ma adnotacji JPA,
 * - Product nie wie, w jakiej tabeli jest zapisany.
 */
@Repository
@Profile("postgres")
public class JdbcProductRepository implements ProductRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcProductRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Zapisuje produkt w bazie danych.
     *
     * Używamy UPSERT-a:
     * - jeśli produkt nie istnieje, zostanie utworzony,
     * - jeśli produkt już istnieje, zostanie zaktualizowany.
     *
     * To pasuje do prostego portu save(...), ale w bardziej rozbudowanej aplikacji
     * można rozdzielić create/update albo dodać optimistic locking.
     */
    @Override
    @Transactional
    public Product save(Product product) {
        jdbcTemplate.update("""
                INSERT INTO catalog.products (
                    id,
                    name,
                    amount,
                    currency,
                    status,
                    created_at,
                    updated_at
                )
                VALUES (?, ?, ?, ?, ?, now(), now())
                ON CONFLICT (id) DO UPDATE SET
                    name = EXCLUDED.name,
                    amount = EXCLUDED.amount,
                    currency = EXCLUDED.currency,
                    status = EXCLUDED.status,
                    updated_at = now()
                """,
                product.id().value(),
                product.name(),
                product.price().amount(),
                product.price().currency().getCurrencyCode(),
                product.status().name()
        );

        return product;
    }

    /**
     * Odczytuje produkt po identyfikatorze.
     *
     * Zwracamy Optional, ponieważ rekord może nie istnieć.
     * Adapter mapuje płaski rekord SQL na pełny obiekt domenowy Product.
     */
    @Override
    public Optional<Product> findById(ProductId id) {
        return jdbcTemplate.query("""
                        SELECT
                            id,
                            name,
                            amount,
                            currency,
                            status
                        FROM catalog.products
                        WHERE id = ?
                        """,
                rs -> {
                    if (!rs.next()) {
                        return Optional.empty();
                    }

                    UUID productId = rs.getObject("id", UUID.class);
                    String name = rs.getString("name");
                    BigDecimal amount = rs.getBigDecimal("amount");
                    Currency currency = Currency.getInstance(rs.getString("currency"));
                    ProductStatus status = ProductStatus.valueOf(rs.getString("status"));

                    Product product = Product.restore(
                            ProductId.of(productId),
                            name,
                            Money.of(amount, currency),
                            status
                    );

                    return Optional.of(product);
                },
                id.value()
        );
    }
}