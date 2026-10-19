package pl.jakubtworek.chatsystem.realtime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import pl.jakubtworek.chatsystem.auth.UserPrincipal;
import pl.jakubtworek.chatsystem.support.TestUsers;
import pl.jakubtworek.chatsystem.user.UserRepository;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class WebSocketTicketServiceTest {
    @Autowired WebSocketTicketService tickets;
    @Autowired UserRepository users;

    @Test
    void ticketIsBoundToOriginAndCanBeConsumedOnlyOnce() {
        var user = TestUsers.create(users, "ticket_alice");
        UUID userId = user.getId();
        IssuedWebSocketTicket issued = tickets.issue(UserPrincipal.from(user), "https://chat.example");

        assertThat(tickets.consume(issued.ticket(), "https://evil.example")).isEmpty();
        assertThat(tickets.consume(issued.ticket(), "https://chat.example"))
                .get().extracting(WebSocketTicketService.TicketIdentity::userId).isEqualTo(userId);
        assertThat(tickets.consume(issued.ticket(), "https://chat.example")).isEmpty();
    }

    @Test
    void expiredTicketFailsClosedAtEntityBoundary() {
        WebSocketTicket ticket = new WebSocketTicket("hash", UUID.randomUUID(), "alice", null,
                Instant.parse("2026-01-01T00:00:00Z"));
        assertThat(ticket.canConsume(Instant.parse("2026-01-01T00:00:01Z"), null)).isFalse();
    }
}
