package pl.jakubtworek.backend_engineering.stage_1.block_c.transactional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Demonstrates REQUIRES_NEW propagation.
 *
 * This service should be placed in a separate bean.
 * Calling this method from another Spring bean goes through proxy,
 * so @Transactional will be applied correctly.
 */
@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Always starts a new independent transaction.
     *
     * If the outer transaction fails, this transaction can still be committed.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logInNewTransaction(String message) {
        auditLogRepository.save(new AuditLog(message));
    }
}