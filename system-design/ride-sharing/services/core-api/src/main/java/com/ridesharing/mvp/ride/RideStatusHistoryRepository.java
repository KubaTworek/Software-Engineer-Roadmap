package com.ridesharing.mvp.ride;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RideStatusHistoryRepository extends JpaRepository<RideStatusHistory, UUID> {
    List<RideStatusHistory> findByRideIdOrderByCreatedAtAsc(UUID rideId);
}
