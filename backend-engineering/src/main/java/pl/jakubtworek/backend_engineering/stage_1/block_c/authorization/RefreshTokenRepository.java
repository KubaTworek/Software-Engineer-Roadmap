package pl.jakubtworek.backend_engineering.stage_1.block_c.authorization;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

/**
 * Repository for refresh tokens.
 */
public interface RefreshTokenRepository
        extends JpaRepository<RefreshToken, Long> {

    @Query("select t.familyId from RefreshToken t where t.tokenHash = :hash")
    Optional<String> findFamilyIdByTokenHash(@Param("hash") String hash);

    // Najstarszy token jest stałym mutexem rodziny. Nie usuwamy go, dopóki rodzina istnieje.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RefreshToken> findFirstByFamilyIdOrderByIdAsc(String familyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findAllByFamilyId(String familyId);
}
