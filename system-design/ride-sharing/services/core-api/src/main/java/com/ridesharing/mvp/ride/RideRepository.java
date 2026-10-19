package com.ridesharing.mvp.ride;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.EntityGraph;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.UUID;
import java.util.Optional;

public interface RideRepository extends JpaRepository<Ride, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Ride> findWithLockById(UUID id);
    @EntityGraph(attributePaths = {"passenger", "driver", "driver.user"})
    Optional<Ride> findPaymentContextById(UUID id);
    List<Ride> findTop100ByStatusOrderByRequestedAtDesc(RideStatus status);
    List<Ride> findTop100ByOrderByRequestedAtDesc();
}
