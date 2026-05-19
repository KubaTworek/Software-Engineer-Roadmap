package pl.jakubtworek.backend_systems_lab_stage_1.block_c.transactional;

import org.springframework.stereotype.Service;

/**
 * Calls transactional logic from another bean.
 *
 * This is the recommended approach.
 */
@Service
public class CorrectSelfInvocationSolutionService {

    private final TransactionalWorkerService workerService;

    public CorrectSelfInvocationSolutionService(
            TransactionalWorkerService workerService
    ) {
        this.workerService = workerService;
    }

    public void execute() {

        /**
         * This call goes through Spring proxy,
         * so @Transactional works correctly.
         */
        workerService.doWorkInTransaction();
    }
}