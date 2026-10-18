package pl.jakubtworek.chatsystem.realtime;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface WebSocketTicketRepository extends JpaRepository<WebSocketTicket, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from WebSocketTicket t where t.tokenHash = :hash")
    Optional<WebSocketTicket> lockByHash(@Param("hash") String hash);
}
