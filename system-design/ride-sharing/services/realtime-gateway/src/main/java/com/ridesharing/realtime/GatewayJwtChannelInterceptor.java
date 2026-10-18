package com.ridesharing.realtime;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Uwierzytelnia sesję na ramce STOMP CONNECT, nie tylko na HTTP handshake.
 * Subskrypcje prywatnych kolejek są dostępne każdemu zalogowanemu użytkownikowi,
 * natomiast miejski strumień GPS jest ograniczony do operatora ADMIN.
 */
@Component
public class GatewayJwtChannelInterceptor implements ChannelInterceptor {
    private final byte[] secret;

    public GatewayJwtChannelInterceptor(@Value("${app.jwt.secret}") String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        var accessor = StompHeaderAccessor.wrap(message);
        if (accessor.getCommand() == StompCommand.CONNECT) {
            String authorization = accessor.getFirstNativeHeader("Authorization");
            if (authorization == null || !authorization.startsWith("Bearer ")) {
                throw new AccessDeniedException("Missing WebSocket bearer token");
            }
            var claims = Jwts.parser()
                    .verifyWith(Keys.hmacShaKeyFor(secret))
                    .build()
                    .parseSignedClaims(authorization.substring(7))
                    .getPayload();
            String role = claims.get("role", String.class);
            accessor.setUser(new UsernamePasswordAuthenticationToken(
                    claims.getSubject(),
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + role))
            ));
        }

        if (accessor.getCommand() == StompCommand.SUBSCRIBE) {
            if (accessor.getUser() == null) {
                throw new AccessDeniedException("Unauthenticated subscription");
            }
            String destination = accessor.getDestination();
            boolean privateQueue = destination != null && destination.startsWith("/user/queue/");
            boolean cityOperations = destination != null
                    && destination.startsWith("/topic/cities/")
                    && accessor.getUser() instanceof UsernamePasswordAuthenticationToken auth
                    && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            if (!privateQueue && !cityOperations) {
                throw new AccessDeniedException("Subscription destination is not allowed");
            }
        }
        return message;
    }
}
