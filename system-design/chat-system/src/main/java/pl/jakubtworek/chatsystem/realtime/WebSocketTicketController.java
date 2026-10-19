package pl.jakubtworek.chatsystem.realtime;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import pl.jakubtworek.chatsystem.auth.UserPrincipal;

/** Ticket powstaje przez chroniony REST; przeglądarka nie umieszcza długowiecznego JWT w URL. */
@RestController
@RequestMapping("/api/websocket-tickets")
public class WebSocketTicketController {
    private final WebSocketTicketService tickets;
    public WebSocketTicketController(WebSocketTicketService tickets) { this.tickets = tickets; }
    @PostMapping
    public IssuedWebSocketTicket issue(@AuthenticationPrincipal UserPrincipal principal, HttpServletRequest request) {
        return tickets.issue(principal, request.getHeader("Origin"));
    }
}
