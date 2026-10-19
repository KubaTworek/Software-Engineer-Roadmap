package pl.jakubtworek.backend.order.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import pl.jakubtworek.backend.order.domain.OrderEntity;

import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import java.util.List;

public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {
    Optional<OrderEntity> findByIdempotencyKey(String idempotencyKey);

    /** PostgreSQL advisory lock serializuje ten sam klucz pomiędzy wszystkimi replikami. */
    @Query(value = "SELECT pg_advisory_xact_lock(hashtext(:key))", nativeQuery = true)
    void lockIdempotencyKey(@Param("key") String key);

    List<OrderEntity> findTop100ByStatusInAndNextAttemptAtBeforeOrderByNextAttemptAtAsc(
            List<pl.jakubtworek.backend.order.domain.OrderStatus> statuses, Instant now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OrderEntity o where o.id = :id")
    Optional<OrderEntity> findByIdForUpdate(UUID id);
}
