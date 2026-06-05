package com.ridesharing.mvp.ride;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;
import java.util.UUID;
import java.util.Optional;

public interface RideRepository extends JpaRepository<Ride, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Ride> findWithLockById(UUID id);
}
