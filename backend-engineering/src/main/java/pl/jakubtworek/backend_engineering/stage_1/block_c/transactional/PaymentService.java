package pl.jakubtworek.backend_engineering.stage_1.block_c.transactional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * A transaction boundary belongs to the complete business operation, not to
 * individual repository calls. Both account changes either commit together or
 * roll back together.
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

    /** Uses optimistic locking through {@link jakarta.persistence.Version}. */
    @Transactional
    public TransferResult transferOptimistically(Long fromId, Long toId, BigDecimal amount) {
        validateCommand(fromId, toId, amount);
        return transferUsingRegularReads(fromId, toId, amount);
    }

    /**
     * Uses row locks for a high-contention invariant. Sorting identifiers gives
     * every transfer the same lock order and reduces the risk of deadlocks.
     */
    @Transactional
    public TransferResult transferPessimistically(Long fromId, Long toId, BigDecimal amount) {
        validateCommand(fromId, toId, amount);

        Long firstLockId = Math.min(fromId, toId);
        Long secondLockId = Math.max(fromId, toId);
        Account firstLocked = findForUpdate(firstLockId);
        Account secondLocked = findForUpdate(secondLockId);

        Account from = fromId.equals(firstLockId) ? firstLocked : secondLocked;
        Account to = toId.equals(secondLockId) ? secondLocked : firstLocked;
        return moveMoney(from, to, amount);
    }

    /**
     * Demonstrates different atomicity scopes. The account changes are rolled
     * back, while the audit entry commits in the service's REQUIRES_NEW transaction.
     */
    @Transactional
    public void transferThenFailAfterIndependentAudit(Long fromId, Long toId, BigDecimal amount) {
        validateCommand(fromId, toId, amount);
        // Do not call the public @Transactional method on this: self-invocation
        // bypasses the proxy. This private method deliberately uses the already
        // active transaction opened for the current entry point.
        TransferResult result = transferUsingRegularReads(fromId, toId, amount);
        auditService.logInNewTransaction("Transfer attempted: %s -> %s, amount=%s"
                .formatted(result.fromAccountId(), result.toAccountId(), amount));
        throw new IllegalStateException("Simulated failure after independent audit");
    }

    private TransferResult transferUsingRegularReads(Long fromId, Long toId, BigDecimal amount) {
        Account from = find(fromId);
        Account to = find(toId);
        return moveMoney(from, to, amount);
    }

    private TransferResult moveMoney(Account from, Account to, BigDecimal amount) {
        from.withdraw(amount);
        to.deposit(amount);

        // No explicit save is needed: JPA dirty checking flushes both managed
        // entities before commit. A failure during flush rolls back both changes.
        return new TransferResult(from.getId(), to.getId(), from.getBalance(), to.getBalance());
    }

    private Account find(Long id) {
        return accountRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + id));
    }

    private Account findForUpdate(Long id) {
        return accountRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + id));
    }

    private static void validateCommand(Long fromId, Long toId, BigDecimal amount) {
        Objects.requireNonNull(fromId, "fromId must not be null");
        Objects.requireNonNull(toId, "toId must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        if (fromId.equals(toId)) {
            throw new IllegalArgumentException("source and destination accounts must differ");
        }
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
    }
}
