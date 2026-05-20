package pl.jakubtworek.backend_engineering.stage_1.block_c.transactional;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Runner used only for demonstration.
 *
 * It shows how different transactional scenarios behave.
 */
@Component
public class TransactionDemoRunner implements CommandLineRunner {

    private final PaymentService paymentService;
    private final OrderService orderService;
    private final RollbackService rollbackService;
    private final SelfInvocationService selfInvocationService;
    private final CorrectSelfInvocationSolutionService correctSolutionService;

    public TransactionDemoRunner(
            PaymentService paymentService,
            OrderService orderService,
            RollbackService rollbackService,
            SelfInvocationService selfInvocationService,
            CorrectSelfInvocationSolutionService correctSolutionService
    ) {
        this.paymentService = paymentService;
        this.orderService = orderService;
        this.rollbackService = rollbackService;
        this.selfInvocationService = selfInvocationService;
        this.correctSolutionService = correctSolutionService;
    }

    @Override
    public void run(String... args) {

        /**
         * REQUIRED + REQUIRES_NEW example.
         *
         * Main transfer may be rolled back,
         * but audit log can still be committed.
         */
        try {
            paymentService.transfer(1L, 2L, BigDecimal.TEN);
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
            rollbackService.rollbackOnRuntimeException();
        } catch (RuntimeException exception) {
            System.out.println("RuntimeException caused rollback");
        }

        /**
         * Checked exception with rollbackFor example.
         */
        try {
            rollbackService.rollbackOnCheckedException();
        } catch (BusinessException exception) {
            System.out.println("Checked exception caused rollback because rollbackFor was used");
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