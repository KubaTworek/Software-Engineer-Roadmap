package com.example.ecommerce.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.time.Instant;
import java.util.List;
import jakarta.persistence.LockModeType;

public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<InventoryReservation> findByOrderIdAndStatus(Long orderId, ReservationStatus status);

    List<InventoryReservation> findTop100ByStatusAndExpiresAtBeforeOrderByIdAsc(
            ReservationStatus status,
            Instant expiresAt
    );
}
