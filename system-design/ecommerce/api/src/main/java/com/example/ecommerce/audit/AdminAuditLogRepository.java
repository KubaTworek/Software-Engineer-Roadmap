package com.example.ecommerce.audit;
import org.springframework.data.jpa.repository.JpaRepository; import java.util.List;
public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> { List<AdminAuditLog> findTop100ByOrderByCreatedAtDesc(); }
