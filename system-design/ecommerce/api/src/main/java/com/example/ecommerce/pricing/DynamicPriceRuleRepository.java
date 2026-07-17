package com.example.ecommerce.pricing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DynamicPriceRuleRepository extends JpaRepository<DynamicPriceRule, Long> {
    List<DynamicPriceRule> findByActiveTrue();
}
