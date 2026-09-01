package pl.jakubtworek.backend_engineering.stage_1.block_c.transactional;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Runner used only for demonstration.
 *
 * It shows how different transactional scenarios behave.
 */
@Component
@Profile("demo")
public class TransactionDemoRunner implements CommandLineRunner {

    private final PaymentService paymentService;
    private final AccountRepository accountRepository;
    private final OrderService orderService;
    private final RollbackService rollbackService;
    private final SelfInvocationService selfInvocationService;
    private final CorrectSelfInvocationSolutionService correctSolutionService;

    public TransactionDemoRunner(
            PaymentService paymentService,
            AccountRepository accountRepository,
            OrderService orderService,
            RollbackService rollbackService,
            SelfInvocationService selfInvocationService,
            CorrectSelfInvocationSolutionService correctSolutionService
    ) {
        this.paymentService = paymentService;
        this.accountRepository = accountRepository;
        this.orderService = orderService;
        this.rollbackService = rollbackService;
        this.selfInvocationService = selfInvocationService;
        this.correctSolutionService = correctSolutionService;
    }

    @Override
    public void run(String... args) {

        Account source = accountRepository.save(new Account(new BigDecimal("100.00")));
        Account destination = accountRepository.save(new Account(new BigDecimal("40.00")));

        /**
         * REQUIRED + REQUIRES_NEW example.
         *
         * Main transfer may be rolled back,
         * but audit log can still be committed.
         */
        try {
            paymentService.transferThenFailAfterIndependentAudit(
                    source.getId(), destination.getId(), BigDecimal.TEN);
        } catch (RuntimeException exception) {
            System.out.println("Transfer failed as expected");
        }

        /**
         * NESTED example.
         *
         * Inner step can roll back to savepoint,
         * while outer transaction continues.
         */
        orderService.placeOrder();

        /**
         * RuntimeException rollback example.
         */
        try {
            rollbackService.writeThenRollbackOnRuntimeException("runtime failure");
        } catch (RuntimeException exception) {
            System.out.println("RuntimeException caused rollback");
        }

        /**
         * Checked exception with rollbackFor example.
         */
        try {
            rollbackService.writeThenRollbackOnCheckedException("checked failure with rollbackFor");
        } catch (BusinessException exception) {
            System.out.println("Checked exception caused rollback because rollbackFor was used");
        }

        try {
            rollbackService.writeThenCommitOnCheckedException("checked failure without rollbackFor");
        } catch (BusinessException exception) {
            System.out.println("Checked exception did not trigger rollback by default");
        }

        /**
         * Incorrect self-invocation example.
         *
         * Transaction will not be started for internal call.
         */
        selfInvocationService.nonTransactionalMethod();

        /**
         * Correct solution.
         *
         * Transactional method is called through another Spring bean.
         */
        correctSolutionService.execute();
    }
}
