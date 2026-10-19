package pl.jakubtworek.backend_engineering.stage_1.block_c.transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * These tests deliberately run without a test-level transaction. Every service
 * invocation must really commit or roll back before the assertion reads state.
 */
@SpringBootTest(properties = {
        "spring.profiles.active=test",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "logging.level.org.hibernate.SQL=OFF",
        "logging.level.org.hibernate.orm.jdbc.bind=OFF"
})
class TransactionBoundaryIntegrationTest {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private RollbackService rollbackService;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @AfterEach
    void cleanDatabase() {
        auditLogRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    void shouldCommitBothSidesOfTransferAtomically() {
        Account from = accountRepository.saveAndFlush(new Account(new BigDecimal("100.00")));
        Account to = accountRepository.saveAndFlush(new Account(new BigDecimal("40.00")));

        paymentService.transferOptimistically(from.getId(), to.getId(), new BigDecimal("25.00"));

        assertThat(balanceOf(from.getId())).isEqualByComparingTo("75.00");
        assertThat(balanceOf(to.getId())).isEqualByComparingTo("65.00");
    }

    @Test
    void shouldRollbackWholeTransferWhenDomainInvariantFails() {
        Account from = accountRepository.saveAndFlush(new Account(new BigDecimal("10.00")));
        Account to = accountRepository.saveAndFlush(new Account(new BigDecimal("40.00")));

        assertThatThrownBy(() -> paymentService.transferOptimistically(
                from.getId(), to.getId(), new BigDecimal("11.00")))
                .isInstanceOf(InsufficientFundsException.class);

        assertThat(balanceOf(from.getId())).isEqualByComparingTo("10.00");
        assertThat(balanceOf(to.getId())).isEqualByComparingTo("40.00");
    }

    @Test
    void shouldCommitRequiresNewAuditButRollbackOuterTransfer() {
        Account from = accountRepository.saveAndFlush(new Account(new BigDecimal("100.00")));
        Account to = accountRepository.saveAndFlush(new Account(new BigDecimal("40.00")));

        assertThatThrownBy(() -> paymentService.transferThenFailAfterIndependentAudit(
                from.getId(), to.getId(), new BigDecimal("25.00")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Simulated failure");

        assertThat(balanceOf(from.getId())).isEqualByComparingTo("100.00");
        assertThat(balanceOf(to.getId())).isEqualByComparingTo("40.00");
        assertThat(auditLogRepository.findAll())
                .singleElement()
                .extracting(AuditLog::getMessage)
                .asString()
                .contains("Transfer attempted");
    }

    @Test
    void shouldApplySpringRollbackRulesToObservableWrites() {
        assertThatThrownBy(() -> rollbackService.writeThenRollbackOnRuntimeException("runtime"))
                .isInstanceOf(RuntimeException.class);
        assertThat(auditLogRepository.count()).isZero();

        assertThatThrownBy(() -> rollbackService.writeThenRollbackOnCheckedException("checked rollback"))
                .isInstanceOf(BusinessException.class);
        assertThat(auditLogRepository.count()).isZero();

        assertThatThrownBy(() -> rollbackService.writeThenCommitOnCheckedException("checked commit"))
                .isInstanceOf(BusinessException.class);
        assertThat(auditLogRepository.findAll())
                .singleElement()
                .extracting(AuditLog::getMessage)
                .isEqualTo("checked commit");
    }

    @Test
    void shouldRejectStaleEntityUsingVersionColumn() {
        Account saved = accountRepository.saveAndFlush(new Account(new BigDecimal("100.00")));
        EntityManager firstSession = entityManagerFactory.createEntityManager();
        EntityManager secondSession = entityManagerFactory.createEntityManager();

        try {
            firstSession.getTransaction().begin();
            secondSession.getTransaction().begin();
            Account firstCopy = firstSession.find(Account.class, saved.getId());
            Account staleCopy = secondSession.find(Account.class, saved.getId());

            firstCopy.withdraw(new BigDecimal("10.00"));
            firstSession.getTransaction().commit();

            staleCopy.withdraw(new BigDecimal("20.00"));
            assertThatThrownBy(() -> secondSession.getTransaction().commit())
                    .hasCauseInstanceOf(OptimisticLockException.class);

            assertThat(balanceOf(saved.getId())).isEqualByComparingTo("90.00");
        } finally {
            rollbackIfActive(firstSession);
            rollbackIfActive(secondSession);
            firstSession.close();
            secondSession.close();
        }
    }

    private BigDecimal balanceOf(Long accountId) {
        return accountRepository.findById(accountId).orElseThrow().getBalance();
    }

    private static void rollbackIfActive(EntityManager entityManager) {
        if (entityManager.getTransaction().isActive()) {
            entityManager.getTransaction().rollback();
        }
    }
}
