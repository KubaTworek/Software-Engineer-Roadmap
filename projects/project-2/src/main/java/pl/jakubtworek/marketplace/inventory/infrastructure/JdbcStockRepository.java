package pl.jakubtworek.marketplace.inventory.infrastructure;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.marketplace.inventory.application.StockRepository;
import pl.jakubtworek.marketplace.inventory.domain.StockItem;

import java.util.Optional;
import java.util.UUID;

/**
 * JDBC implementacja portu StockRepository.
 *
 * Ta klasa jest adapterem infrastrukturalnym modułu Inventory.
 * Implementuje port z warstwy aplikacyjnej i ukrywa szczegóły PostgreSQL/JDBC
 * przed use case’ami oraz modelem domenowym.
 *
 * Ważne:
 * - domena nie zna tabeli inventory.stock_items,
 * - domena nie zna JdbcTemplate,
 * - use case zależy tylko od StockRepository,
 * - ten adapter jest aktywny tylko dla profilu postgres.
 */
@Repository
@Profile("postgres")
public class JdbcStockRepository implements StockRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcStockRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Zapisuje StockItem do bazy danych.
     *
     * Używamy UPSERT-a:
     * - jeśli stock dla productId jeszcze nie istnieje, tworzymy nowy rekord,
     * - jeśli istnieje, aktualizujemy ilości.
     *
     * To pasuje do portu save(...), który nie rozróżnia insert/update.
     */
    @Override
    @Transactional
    public StockItem save(StockItem item) {
        jdbcTemplate.update("""
                INSERT INTO inventory.stock_items (
                    product_id,
                    available_quantity,
                    reserved_quantity,
                    created_at,
                    updated_at
                )
                VALUES (?, ?, ?, now(), now())
                ON CONFLICT (product_id) DO UPDATE SET
                    available_quantity = EXCLUDED.available_quantity,
                    reserved_quantity = EXCLUDED.reserved_quantity,
                    updated_at = now()
                """,
                item.productId(),
                item.availableQuantity(),
                item.reservedQuantity()
        );

        return item;
    }

    /**
     * Odczytuje stan magazynowy po identyfikatorze produktu.
     *
     * Adapter mapuje płaski rekord z tabeli inventory.stock_items na agregat domenowy
     * StockItem przez metodę restore(...).
     */
    @Override
    public Optional<StockItem> findByProductId(UUID productId) {
        return jdbcTemplate.query("""
                        SELECT
                            product_id,
                            available_quantity,
                            reserved_quantity
                        FROM inventory.stock_items
                        WHERE product_id = ?
                        """,
                rs -> {
                    if (!rs.next()) {
                        return Optional.empty();
                    }

                    StockItem item = StockItem.restore(
                            rs.getObject("product_id", UUID.class),
                            rs.getInt("available_quantity"),
                            rs.getInt("reserved_quantity")
                    );

                    return Optional.of(item);
                },
                productId
        );
    }
}