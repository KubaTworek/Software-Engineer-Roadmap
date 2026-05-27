package pl.jakubtworek.booking.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pl.jakubtworek.booking.entity.CapacityPool;

import java.util.Optional;
import java.util.UUID;

public interface CapacityPoolRepository extends JpaRepository<CapacityPool, UUID> {
    Optional<CapacityPool> findByEventId(UUID eventId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update CapacityPool pool
               set pool.availableCapacity = pool.availableCapacity - 1
             where pool.event.id = :eventId
               and pool.availableCapacity > 0
            """)
    int reserveOneSeatIfAvailable(@Param("eventId") UUID eventId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update CapacityPool pool
               set pool.availableCapacity = pool.availableCapacity + 1
             where pool.event.id = :eventId
               and pool.availableCapacity < pool.totalCapacity
            """)
    int releaseOneSeat(@Param("eventId") UUID eventId);
}
