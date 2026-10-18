package pl.jakubtworek.chatsystem.conversation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;

import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {
    Optional<Conversation> findByDirectKey(String directKey);

    /** Serializuje przydzielanie sequence wyłącznie w obrębie jednej rozmowy. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Conversation c where c.id = :id")
    Optional<Conversation> findForMessageAppend(@Param("id") UUID id);
}
