package pl.jakubtworek.marketplace.payment.infrastructure;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.marketplace.payment.application.PaymentRepository;
import pl.jakubtworek.marketplace.payment.domain.Payment;
import pl.jakubtworek.marketplace.payment.domain.PaymentId;
import pl.jakubtworek.marketplace.payment.domain.PaymentStatus;
import pl.jakubtworek.marketplace.shared.kernel.Money;

import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JDBC implementacja portu PaymentRepository.
 *
 * Ta klasa jest adapterem infrastrukturalnym modułu Payment.
 * Implementuje port z warstwy aplikacyjnej i ukrywa szczegóły PostgreSQL/JDBC
 * przed domeną oraz use case’ami.
 *
 * Domena pozostaje czysta:
 * - Payment nie zna JdbcTemplate,
 * - Payment nie zna tabeli payment.payments,
 * - Payment nie ma adnotacji JPA,
 * - application layer zależy tylko od PaymentRepository.
 */
@Repository
@Profile("postgres")
public class JdbcPaymentRepository implements PaymentRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcPaymentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Zapisuje agregat Payment do bazy danych.
     *
     * Używamy UPSERT-a:
     * - jeśli płatność jeszcze nie istnieje, zostanie utworzona,
     * - jeśli istnieje, zostanie zaktualizowana.
     *
     * order_id jest unikalny, ponieważ w tym modelu jedno zamówienie ma jedną płatność.
     */
    @Override
    @Transactional
    public Payment save(Payment payment) {
        jdbcTemplate.update("""
                INSERT INTO payment.payments (
                    id,
                    order_id,
                    amount,
                    currency,
                    status,
                    created_at,
                    updated_at
                )
                VALUES (?, ?, ?, ?, ?, now(), now())
                ON CONFLICT (id) DO UPDATE SET
                    order_id = EXCLUDED.order_id,
                    amount = EXCLUDED.amount,
                    currency = EXCLUDED.currency,
                    status = EXCLUDED.status,
                    updated_at = now()
                """,
                payment.id().value(),
                payment.orderId(),
                payment.amount().amount(),
                payment.amount().currency().getCurrencyCode(),
                payment.status().name()
        );

        return payment;
    }

    /**
     * Wyszukuje płatność po identyfikatorze zamówienia.
     *
     * To jest główny sposób odczytu w obecnym porcie PaymentRepository.
     * Zwracamy Optional, bo płatność dla zamówienia może jeszcze nie istnieć.
     */
    @Override
    public Optional<Payment> findByOrderId(UUID orderId) {
        List<PaymentRow> rows = jdbcTemplate.query("""
                        SELECT
                            id,
                            order_id,
                            amount,
                            currency,
                            status
                        FROM payment.payments
                        WHERE order_id = ?
                        """,
                (rs, rowNum) -> new PaymentRow(
                        rs.getObject("id", UUID.class),
                        rs.getObject("order_id", UUID.class),
                        rs.getBigDecimal("amount"),
                        rs.getString("currency"),
                        rs.getString("status")
                ),
                orderId
        );

        if (rows.isEmpty()) {
            return Optional.empty();
        }

        PaymentRow row = rows.get(0);

        Payment payment = Payment.restore(
                PaymentId.of(row.id()),
                row.orderId(),
                Money.of(
                        row.amount(),
                        Currency.getInstance(row.currency())
                ),
                PaymentStatus.valueOf(row.status())
        );
        return Optional.of(payment);
    }

    /**
     * Techniczny rekord pomocniczy reprezentujący wiersz z tabeli payment.payments.
     *
     * Nie jest to obiekt domenowy. Służy tylko do przeniesienia danych z ResultSet
     * do rekonstrukcji agregatu Payment.
     */
    private record PaymentRow(
            UUID id,
            UUID orderId,
            java.math.BigDecimal amount,
            String currency,
            String status
    ) {
    }
}