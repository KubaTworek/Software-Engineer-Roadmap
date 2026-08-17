package com.example.ecommerce.marketplace;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SellerRepository extends JpaRepository<Seller, Long> {
    Optional<Seller> findByOwnerId(Long ownerId);
    Optional<Seller> findBySlug(String slug);
    List<Seller> findByStatus(SellerStatus status);
}
