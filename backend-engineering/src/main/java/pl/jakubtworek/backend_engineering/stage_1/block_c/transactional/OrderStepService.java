package pl.jakubtworek.backend_engineering.stage_1.block_c.transactional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Demonstrates NESTED propagation.
 *
 * NESTED uses database savepoints.
 * It works only with transaction managers and databases
 * that support savepoints, for example DataSourceTransactionManager.
 */
@Service
public class OrderStepService {

    /**
     * Creates a nested transaction inside an existing transaction.
     *
     * If this method fails, Spring can roll back only to the savepoint,
     * while the outer transaction may continue.
     */
    @Transactional(propagation = Propagation.NESTED)
    public void executeOptionalStep() {

        System.out.println("Executing nested step");

        throw new RuntimeException("Nested step failed");
    }
}