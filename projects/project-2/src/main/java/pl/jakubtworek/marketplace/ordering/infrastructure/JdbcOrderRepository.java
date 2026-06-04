package pl.jakubtworek.marketplace.ordering.infrastructure;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.marketplace.catalog.domain.ProductId;
import pl.jakubtworek.marketplace.ordering.application.OrderRepository;
import pl.jakubtworek.marketplace.ordering.domain.CustomerId;
import pl.jakubtworek.marketplace.ordering.domain.Order;
import pl.jakubtworek.marketplace.ordering.domain.OrderId;
import pl.jakubtworek.marketplace.ordering.domain.OrderLine;
import pl.jakubtworek.marketplace.ordering.domain.OrderStatus;
import pl.jakubtworek.marketplace.shared.kernel.Money;

import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC implementacja portu OrderRepository.
 *
 * Ta klasa jest adapterem infrastrukturalnym modułu Ordering.
 * Implementuje port z warstwy aplikacyjnej i ukrywa szczegóły PostgreSQL/JDBC
 * przed warstwą aplikacyjną oraz domeną.
 *
 * Domena pozostaje czysta:
 * - Order nie zna JDBC,
 * - Order nie zna tabel ordering.orders ani ordering.order_lines,
 * - Order nie ma adnotacji JPA,
 * - use case zależy tylko od OrderRepository.
 */
@Repository
@Profile("postgres")
public class JdbcOrderRepository implements OrderRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcOrderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Zapisuje agregat Order do bazy danych.
     *
     * Agregat Order jest zapisany w dwóch tabelach:
     * - ordering.orders jako nagłówek zamówienia,
     * - ordering.order_lines jako linie zamówienia.
     *
     * Używamy UPSERT-a dla tabeli orders.
     * Linie zamówienia są usuwane i wstawiane ponownie, ponieważ w obecnym modelu
     * lista linii jest niemutowalna po utworzeniu zamówienia.
     *
     * Gdyby linie były edytowalne, warto byłoby zrobić dokładniejszy update/diff.
     */
    @Override
    @Transactional
    public Order save(Order order) {
        jdbcTemplate.update("""
                INSERT INTO ordering.orders (
                    id,
                    customer_id,
                    status,
                    payment_reserved,
                    stock_reserved,
                    created_at,
                    updated_at
                )
                VALUES (?, ?, ?, ?, ?, now(), now())
                ON CONFLICT (id) DO UPDATE SET
                    customer_id = EXCLUDED.customer_id,
                    status = EXCLUDED.status,
                    payment_reserved = EXCLUDED.payment_reserved,
                    stock_reserved = EXCLUDED.stock_reserved,
                    updated_at = now()
                """,
                order.id().value(),
                order.customerId().value(),
                order.status().name(),
                order.paymentReserved(),
                order.stockReserved()
        );

        jdbcTemplate.update(
                "DELETE FROM ordering.order_lines WHERE order_id = ?",
                order.id().value()
        );

        int lineNumber = 1;

        for (OrderLine line : order.lines()) {
            jdbcTemplate.update("""
                    INSERT INTO ordering.order_lines (
                        order_id,
                        line_number,
                        product_id,
                        quantity,
                        unit_amount,
                        currency
                    )
                    VALUES (?, ?, ?, ?, ?, ?)
                    """,
                    order.id().value(),
                    lineNumber++,
                    line.productId().value(),
                    line.quantity(),
                    line.unitPrice().amount(),
                    line.unitPrice().currency().getCurrencyCode()
            );
        }

        return order;
    }

    /**
     * Odczytuje zamówienie po identyfikatorze.
     *
     * Adapter:
     * - odczytuje nagłówek zamówienia z ordering.orders,
     * - odczytuje linie z ordering.order_lines,
     * - odtwarza agregat Order przez Order.restore(...).
     *
     * Zwracamy Optional, ponieważ zamówienie może nie istnieć.
     */
    @Override
    public Optional<Order> findById(OrderId id) {
        List<OrderHeaderRow> headers = jdbcTemplate.query("""
                        SELECT
                            id,
                            customer_id,
                            status,
                            payment_reserved,
                            stock_reserved
                        FROM ordering.orders
                        WHERE id = ?
                        """,
                (rs, rowNum) -> new OrderHeaderRow(
                        rs.getObject("id", UUID.class),
                        rs.getObject("customer_id", UUID.class),
                        rs.getString("status"),
                        rs.getBoolean("payment_reserved"),
                        rs.getBoolean("stock_reserved")
                ),
                id.value()
        );

        if (headers.isEmpty()) {
            return Optional.empty();
        }

        OrderHeaderRow header = headers.get(0);

        List<OrderLine> lines = jdbcTemplate.query("""
                        SELECT
                            product_id,
                            quantity,
                            unit_amount,
                            currency
                        FROM ordering.order_lines
                        WHERE order_id = ?
                        ORDER BY line_number ASC
                        """,
                (rs, rowNum) -> new OrderLine(
                        ProductId.of(rs.getObject("product_id", UUID.class)),
                        rs.getInt("quantity"),
                        Money.of(
                                rs.getBigDecimal("unit_amount"),
                                Currency.getInstance(rs.getString("currency"))
                        )
                ),
                id.value()
        );

        Order order = Order.restore(
                OrderId.of(header.id()),
                CustomerId.of(header.customerId()),
                lines,
                OrderStatus.valueOf(header.status()),
                header.paymentReserved(),
                header.stockReserved()
        );

        return Optional.of(order);
    }

    /**
     * Techniczny rekord pomocniczy reprezentujący nagłówek zamówienia odczytany z bazy.
     *
     * Nie jest to obiekt domenowy. Służy tylko do tymczasowego przeniesienia danych
     * między ResultSet a rekonstrukcją agregatu Order.
     */
    private record OrderHeaderRow(
            UUID id,
            UUID customerId,
            String status,
            boolean paymentReserved,
            boolean stockReserved
    ) {
    }
}