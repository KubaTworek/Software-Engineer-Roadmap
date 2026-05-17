package pl.jakubtworek.backend_systems_lab_stage_1.block_c.transactional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Demonstrates how NESTED propagation can allow partial rollback.
 */
@Service
public class OrderService {

    private final OrderStepService orderStepService;

    public OrderService(OrderStepService orderStepService) {
        this.orderStepService = orderStepService;
    }

    /**
     * Outer transaction.
     *
     * The nested step may fail and roll back to savepoint,
     * but this outer transaction can still continue.
     */
    @Transactional
    public void placeOrder() {

        System.out.println("Main order logic started");

        try {
            orderStepService.executeOptionalStep();
        } catch (RuntimeException exception) {
            /**
             * The nested transaction failed,
             * but the outer transaction is still allowed to continue.
             */
            System.out.println("Nested step failed, continuing order process");
        }

        System.out.println("Main order logic finished");
    }
}