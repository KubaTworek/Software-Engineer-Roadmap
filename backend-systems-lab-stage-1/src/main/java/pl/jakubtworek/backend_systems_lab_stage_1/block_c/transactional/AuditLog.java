package pl.jakubtworek.backend_systems_lab_stage_1.block_c.transactional;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Audit log entity.
 *
 * It is used to demonstrate REQUIRES_NEW propagation,
 * where logging is committed independently from the main transaction.
 */
@Entity
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String message;

    private LocalDateTime createdAt;

    protected AuditLog() {
        // Required by JPA
    }

    public AuditLog(String message) {
        this.message = message;
        this.createdAt = LocalDateTime.now();
    }
}