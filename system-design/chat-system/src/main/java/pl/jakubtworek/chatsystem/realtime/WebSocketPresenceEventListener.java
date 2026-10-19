package pl.jakubtworek.chatsystem.realtime;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import pl.jakubtworek.chatsystem.conversation.ConversationMemberRepository;
import pl.jakubtworek.chatsystem.presence.PresenceLeaseService;
import pl.jakubtworek.chatsystem.presence.PresenceResponse;
import pl.jakubtworek.chatsystem.presence.PresenceLeaseExpiredEvent;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.security.Principal;
import java.util.UUID;

/**
 * Łączy lifecycle sesji STOMP ze współdzielonym lease'em presence. Sam disconnect nie jest dowodem,
 * że użytkownik jest offline: może mieć drugie urządzenie albo sesję na innym gatewayu.
 */
@Component
public class WebSocketPresenceEventListener {
    private final PresenceLeaseService leases;
    private final ConversationMemberRepository members;
    private final RealtimeMessagePublisher publisher;

    public WebSocketPresenceEventListener(PresenceLeaseService leases, ConversationMemberRepository members,
                                          RealtimeMessagePublisher publisher) {
        this.leases = leases; this.members = members; this.publisher = publisher;
    }

    @EventListener
    public void handleSessionConnected(SessionConnectedEvent event) {
        UUID userId = userId(event.getUser());
        String sessionId = StompHeaderAccessor.wrap(event.getMessage()).getSessionId();
        if (userId != null && sessionId != null) {
            leases.connected(sessionId, userId).ifPresent(state -> publish(userId, state));
        }
    }

    @EventListener
    public void handleSessionDisconnected(SessionDisconnectEvent event) {
        UUID userId = userId(event.getUser());
        String sessionId = StompHeaderAccessor.wrap(event.getMessage()).getSessionId();
        if (userId != null && sessionId != null) {
            leases.disconnected(sessionId, userId).ifPresent(state -> publish(userId, state));
        }
    }

    /** Cleaner TTL nie ma zależności do transportu; event po commitcie domyka fan-out OFFLINE. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleExpiredLease(PresenceLeaseExpiredEvent event) {
        publish(event.userId(), event.presence());
    }

    private void publish(UUID userId, PresenceResponse state) {
        members.findMembershipsForUser(userId).forEach(member ->
                publisher.publishPresence(member.getConversation().getId(), state));
    }

    private UUID userId(Principal principal) {
        if (principal == null) return null;
        try { return UUID.fromString(principal.getName()); }
        catch (IllegalArgumentException ignored) { return null; }
    }
}
