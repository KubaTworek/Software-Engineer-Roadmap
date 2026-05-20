package pl.jakubtworek.backend_engineering.stage_1.block_c.transactional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Demonstrates rollback behavior.
 */
@Service
public class RollbackService {

    /**
     * RuntimeException triggers rollback by default.
     */
    @Transactional
    public void rollbackOnRuntimeException() {
        throw new RuntimeException("Rollback will happen");
    }

    /**
     * Checked exceptions do not trigger rollback by default.
     *
     * rollbackFor explicitly tells Spring to roll back
     * when BusinessException is thrown.
     */
    @Transactional(rollbackFor = BusinessException.class)
    public void rollbackOnCheckedException() throws BusinessException {
        throw new BusinessException("Rollback will happen because rollbackFor is used");
    }

    /**
     * Without rollbackFor, this checked exception would not roll back
     * the transaction by default.
     */
    @Transactional
    public void noRollbackForCheckedException() throws BusinessException {
        throw new BusinessException("Rollback will NOT happen by default");
    }
}