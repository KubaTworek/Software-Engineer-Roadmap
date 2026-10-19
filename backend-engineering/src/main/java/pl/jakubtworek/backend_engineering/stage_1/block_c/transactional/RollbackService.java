package pl.jakubtworek.backend_engineering.stage_1.block_c.transactional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Demonstrates rollback behavior.
 */
@Service
public class RollbackService {

    private final AuditLogRepository auditLogRepository;

    public RollbackService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * RuntimeException triggers rollback by default.
     */
    @Transactional
    public void writeThenRollbackOnRuntimeException(String message) {
        auditLogRepository.save(new AuditLog(message));
        throw new RuntimeException("Rollback will happen");
    }

    /**
     * Checked exceptions do not trigger rollback by default.
     *
     * rollbackFor explicitly tells Spring to roll back
     * when BusinessException is thrown.
     */
    @Transactional(rollbackFor = BusinessException.class)
    public void writeThenRollbackOnCheckedException(String message) throws BusinessException {
        auditLogRepository.save(new AuditLog(message));
        throw new BusinessException("Rollback will happen because rollbackFor is used");
    }

    /**
     * Without rollbackFor, this checked exception would not roll back
     * the transaction by default.
     */
    @Transactional
    public void writeThenCommitOnCheckedException(String message) throws BusinessException {
        auditLogRepository.save(new AuditLog(message));
        throw new BusinessException("Rollback will NOT happen by default");
    }
}
