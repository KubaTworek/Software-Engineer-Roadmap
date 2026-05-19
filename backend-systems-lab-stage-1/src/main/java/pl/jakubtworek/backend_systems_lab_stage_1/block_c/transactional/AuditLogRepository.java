package pl.jakubtworek.backend_systems_lab_stage_1.block_c.transactional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for audit logs.
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
}