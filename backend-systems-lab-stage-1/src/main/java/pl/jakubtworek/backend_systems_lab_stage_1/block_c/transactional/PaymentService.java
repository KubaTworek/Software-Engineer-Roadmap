package pl.jakubtworek.backend_systems_lab_stage_1.block_c.transactional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Demonstrates default propagation: REQUIRED.
 *
 * REQUIRED means:
 * - join existing transaction if one exists,
 * - otherwise create a new transaction.
 */
@Service
public class PaymentService {

    private final AccountRepository accountRepository;
    private final AuditService auditService;

    public PaymentService(
            AccountRepository accountRepository,
            AuditService auditService
    ) {
        this.accountRepository = accountRepository;
        this.auditService = auditService;
    }

    /**
     * Main business transaction.
     *
     * By default, propagation = REQUIRED.
     * If RuntimeException is thrown, the whole transaction is rolled back.
     */
    @Transactional
    public void transfer(Long fromId, Long toId, BigDecimal amount) {

        Account from = accountRepository.findById(fromId)
                .orElseThrow();

        Account to = accountRepository.findById(toId)
                .orElseThrow();

        from.withdraw(amount);
        to.deposit(amount);

        accountRepository.save(from);
        accountRepository.save(to);

        /**
         * This audit operation uses REQUIRES_NEW.
         *
         * It will be committed in a separate transaction.
         */
        auditService.logInNewTransaction("Transfer executed");

        /**
         * This exception rolls back the transfer transaction,
         * but the audit log may already be committed.
         */
        throw new RuntimeException("Transfer failed after audit log");
    }
}