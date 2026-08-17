package com.example.ecommerce.returns;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReturnRequestRepository extends JpaRepository<ReturnRequest, Long> {
    List<ReturnRequest> findByUserIdOrderByCreatedAtDesc(Long userId);
}
