package pl.jakubtworek.booking.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.jakubtworek.booking.entity.CapacityPool;

import java.util.Optional;
import java.util.UUID;

public interface CapacityPoolRepository extends JpaRepository<CapacityPool, UUID> {
    Optional<CapacityPool> findByEventId(UUID eventId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select pool from CapacityPool pool where pool.event.id = :eventId")
    Optional<CapacityPool> findByEventIdForUpdate(@Param("eventId") UUID eventId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE capacity_pools
               SET available_capacity = :availableCapacity
             WHERE id = :poolId
            """, nativeQuery = true)
    int blindSetAvailableCapacity(
            @Param("poolId") UUID poolId,
            @Param("availableCapacity") int availableCapacity
    );

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE capacity_pools
               SET available_capacity = available_capacity - 1
             WHERE event_id = :eventId
               AND available_capacity > 0
            """, nativeQuery = true)
    int reserveOneSeatIfAvailable(@Param("eventId") UUID eventId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE capacity_pools
               SET available_capacity = available_capacity + 1
             WHERE event_id = :eventId
               AND available_capacity < total_capacity
            """, nativeQuery = true)
    int releaseOneSeat(@Param("eventId") UUID eventId);
}
