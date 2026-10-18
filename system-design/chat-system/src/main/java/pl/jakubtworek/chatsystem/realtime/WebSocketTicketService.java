package pl.jakubtworek.chatsystem.realtime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pl.jakubtworek.chatsystem.auth.UserPrincipal;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;

@Service
public class WebSocketTicketService {
    private final WebSocketTicketRepository tickets;
    private final Clock clock;
    private final Duration ttl;
    private final SecureRandom random = new SecureRandom();
    public WebSocketTicketService(WebSocketTicketRepository tickets, Clock clock,
            @Value("${app.websocket.ticket-ttl-seconds:30}") long ttlSeconds) {
        this.tickets = tickets; this.clock = clock; this.ttl = Duration.ofSeconds(ttlSeconds);
    }
    @Transactional
    public IssuedWebSocketTicket issue(UserPrincipal principal, String origin) {
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiresAt = clock.instant().plus(ttl);
        tickets.save(new WebSocketTicket(hash(raw), principal.id(), principal.username(), normalize(origin), expiresAt));
        return new IssuedWebSocketTicket(raw, expiresAt);
    }
    @Transactional
    public Optional<TicketIdentity> consume(String raw, String origin) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        Instant now = clock.instant();
        return tickets.lockByHash(hash(raw)).filter(ticket -> ticket.canConsume(now, normalize(origin)))
                .map(ticket -> { ticket.consume(now); tickets.save(ticket);
                    return new TicketIdentity(ticket.getUserId(), ticket.getUsername()); });
    }
    private String normalize(String origin) { return origin == null || origin.isBlank() ? null : origin.trim(); }
    private String hash(String raw) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(raw.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    public record TicketIdentity(UUID userId, String username) {}
}
