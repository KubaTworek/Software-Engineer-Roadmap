package com.example.ecommerce.inventory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, Long> {
    List<InventoryReservation> findByOrderIdAndStatus(Long orderId, ReservationStatus status);

    List<InventoryReservation> findTop100ByStatusAndExpiresAtBeforeOrderByIdAsc(
            ReservationStatus status,
            Instant expiresAt
    );
}
