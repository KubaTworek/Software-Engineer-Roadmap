package com.ridesharing.mvp.driver;

import com.ridesharing.mvp.user.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

public interface DriverRepository extends JpaRepository<Driver, UUID> {
    Optional<Driver> findByUser(AppUser user);
    Optional<Driver> findByUserId(UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Driver> findWithLockById(UUID id);
}
