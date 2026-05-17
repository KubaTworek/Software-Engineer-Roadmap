package pl.jakubtworek.backend_systems_lab_stage_1.block_c.transactional;

import jakarta.persistence.*;
import java.math.BigDecimal;

/**
 * Simple entity used to demonstrate transactional operations.
 */
@Entity
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private BigDecimal balance;

    protected Account() {
        // Required by JPA
    }

    public Account(BigDecimal balance) {
        this.balance = balance;
    }

    public Long getId() {
        return id;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void withdraw(BigDecimal amount) {
        this.balance = this.balance.subtract(amount);
    }

    public void deposit(BigDecimal amount) {
        this.balance = this.balance.add(amount);
    }
}