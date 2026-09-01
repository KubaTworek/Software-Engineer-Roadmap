package pl.jakubtworek.backend_engineering.stage_1.block_c.transactional;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Simple entity used to demonstrate transactional operations.
 */
@Entity
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private long version;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    protected Account() {
        // Required by JPA
    }

    public Account(BigDecimal balance) {
        this.balance = requireNonNegative(balance);
    }

    public Long getId() {
        return id;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public long getVersion() {
        return version;
    }

    public void withdraw(BigDecimal amount) {
        BigDecimal validatedAmount = requirePositive(amount);
        if (balance.compareTo(validatedAmount) < 0) {
            throw new InsufficientFundsException(id, balance, validatedAmount);
        }
        balance = balance.subtract(validatedAmount);
    }

    public void deposit(BigDecimal amount) {
        balance = balance.add(requirePositive(amount));
    }

    private static BigDecimal requireNonNegative(BigDecimal value) {
        Objects.requireNonNull(value, "balance must not be null");
        if (value.signum() < 0) {
            throw new IllegalArgumentException("balance must not be negative");
        }
        return value;
    }

    private static BigDecimal requirePositive(BigDecimal value) {
        Objects.requireNonNull(value, "amount must not be null");
        if (value.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        return value;
    }
}
