package pl.jakubtworek.chatsystem.message;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MessageReceiptRepository extends JpaRepository<MessageReceipt, UUID> {
    Optional<MessageReceipt> findByMessageIdAndRecipientId(UUID messageId, UUID recipientId);

    @Query("""
            select r from MessageReceipt r
            join fetch r.message m
            join fetch r.recipient
            where r.conversation.id = :conversationId
              and r.recipient.id = :recipientId
              and m.id in :messageIds
            """)
    List<MessageReceipt> findReceiptsForMessages(
            @Param("conversationId") UUID conversationId,
            @Param("recipientId") UUID recipientId,
            @Param("messageIds") List<UUID> messageIds
    );

    @Query("""
            select r from MessageReceipt r
            join fetch r.message m
            join fetch r.recipient
            where r.conversation.id = :conversationId
              and r.recipient.id = :recipientId
              and m.createdAt <= (
                    select boundary.createdAt from Message boundary where boundary.id = :upToMessageId
              )
              and r.readAt is null
            """)
    List<MessageReceipt> findUnreadReceiptsUpTo(
            @Param("conversationId") UUID conversationId,
            @Param("recipientId") UUID recipientId,
            @Param("upToMessageId") UUID upToMessageId
    );

    @Query("""
            select r from MessageReceipt r
            join fetch r.message m
            join fetch r.recipient
            where r.conversation.id = :conversationId
              and r.recipient.id = :recipientId
              and m.createdAt <= (
                    select boundary.createdAt from Message boundary where boundary.id = :upToMessageId
              )
              and r.deliveredAt is null
            """)
    List<MessageReceipt> findUndeliveredReceiptsUpTo(
            @Param("conversationId") UUID conversationId,
            @Param("recipientId") UUID recipientId,
            @Param("upToMessageId") UUID upToMessageId
    );

    @Query("""
            select r from MessageReceipt r
            join fetch r.message m
            join fetch r.recipient
            where m.id in :messageIds
            """)
    List<MessageReceipt> findAllForMessages(@Param("messageIds") List<UUID> messageIds);

    long countByConversationIdAndRecipientIdAndReadAtIsNull(UUID conversationId, UUID recipientId);
}
