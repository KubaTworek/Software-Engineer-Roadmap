package com.example.ecommerce.integration.erp;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ErpSyncJobRepository extends JpaRepository<ErpSyncJob, Long> {
    List<ErpSyncJob> findTop100ByStatusOrderByIdAsc(ErpSyncStatus status);
}
