package pl.jakubtworek.chatsystem.presence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PresenceSessionRepository extends JpaRepository<PresenceSession, String> {
    long countByUserIdAndExpiresAtAfter(UUID userId, Instant now);
    @Query("select distinct s.userId from PresenceSession s where s.expiresAt <= :now")
    List<UUID> findUsersWithExpiredSessions(@Param("now") Instant now);
    @Modifying @Query("delete from PresenceSession s where s.expiresAt <= :now")
    int deleteExpired(@Param("now") Instant now);
}
