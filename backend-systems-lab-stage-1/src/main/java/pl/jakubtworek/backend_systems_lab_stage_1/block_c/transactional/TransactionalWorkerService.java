package pl.jakubtworek.backend_systems_lab_stage_1.block_c.transactional;

import org.springframework.stereotype.Service;

/**
 * Correct solution for self-invocation problem.
 *
 * The transactional method is moved to another Spring bean,
 * so the call goes through proxy.
 */
@Service
public class TransactionalWorkerService {

    /**
     * This method will be intercepted correctly
     * because it is called from another bean.
     */
    @org.springframework.transaction.annotation.Transactional
    public void doWorkInTransaction() {
        System.out.println("Work inside transaction");
    }
}