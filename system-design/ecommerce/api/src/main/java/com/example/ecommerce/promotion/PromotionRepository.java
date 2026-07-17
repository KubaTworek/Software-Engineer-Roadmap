package com.example.ecommerce.promotion;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PromotionRepository extends JpaRepository<Promotion, Long> {
    List<Promotion> findByStatusOrderByPriorityAsc(PromotionStatus status);
}
