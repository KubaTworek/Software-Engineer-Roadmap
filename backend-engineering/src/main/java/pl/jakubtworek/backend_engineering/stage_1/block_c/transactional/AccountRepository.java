package pl.jakubtworek.backend_engineering.stage_1.block_c.transactional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;

/**
 * Repository managed by Spring.
 *
 * Repository methods are usually transactional by default,
 * but business transactions should be controlled at service layer.
 */
public interface AccountRepository extends JpaRepository<Account, Long> {

    /**
     * Acquires a database row lock until the surrounding transaction completes.
     * The method must therefore be invoked inside a transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from Account account where account.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") Long id);
}
