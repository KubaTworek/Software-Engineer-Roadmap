package pl.jakubtworek.chatsystem.realtime;
import java.time.Instant;
public record IssuedWebSocketTicket(String ticket, Instant expiresAt) {}
