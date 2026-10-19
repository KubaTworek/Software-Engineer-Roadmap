package pl.jakubtworek.chatsystem.realtime;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.socket.WebSocketHandler;
import pl.jakubtworek.chatsystem.auth.JwtService;
import pl.jakubtworek.chatsystem.presence.PresenceLeaseService;

import java.util.HashMap;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class WebSocketSecurityTest {
    @Test
    void handshakeAcceptsAuthorizationHeaderButRejectsQueryTokenByDefault() {
        JwtService jwt = mock(JwtService.class);
        UUID userId = UUID.randomUUID();
        when(jwt.extractUserId("good-token")).thenReturn(userId);
        when(jwt.extractUsername("good-token")).thenReturn("alice");
        WebSocketTicketService tickets = mock(WebSocketTicketService.class);
        WebSocketJwtHandshakeInterceptor interceptor = new WebSocketJwtHandshakeInterceptor(jwt, tickets, false);
        WebSocketHandler handler = mock(WebSocketHandler.class);

        ServerHttpRequest queryOnly = request("/ws", "token=good-token", null);
        assertThat(interceptor.beforeHandshake(queryOnly, null, handler, new HashMap<>())).isFalse();

        ServerHttpRequest authorized = request("/ws", null, "Bearer good-token");
        var attributes = new HashMap<String, Object>();
        assertThat(interceptor.beforeHandshake(authorized, null, handler, attributes)).isTrue();
        assertThat(attributes).containsEntry(WebSocketJwtHandshakeInterceptor.USER_ID_ATTRIBUTE, userId);
    }

    @Test
    void publicConversationTopicIsFailClosedAndPrivateQueueRenewsLease() {
        PresenceLeaseService leases = mock(PresenceLeaseService.class);
        WebSocketAuthorizationInterceptor interceptor = new WebSocketAuthorizationInterceptor(leases);
        UUID userId = UUID.randomUUID();

        Message<byte[]> publicTopic = subscribe(userId, "/topic/conversations/" + UUID.randomUUID());
        MessageChannel channel = mock(MessageChannel.class);
        assertThatThrownBy(() -> interceptor.preSend(publicTopic, channel))
                .isInstanceOf(AccessDeniedException.class);

        Message<byte[]> privateQueue = subscribe(userId, "/user/queue/conversations/" + UUID.randomUUID());
        assertThat(interceptor.preSend(privateQueue, channel)).isSameAs(privateQueue);
        verify(leases, times(2)).heartbeat("session-1", userId);
    }

    private Message<byte[]> subscribe(UUID userId, String destination) {
        StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        headers.setSessionId("session-1");
        headers.setDestination(destination);
        headers.setUser(() -> userId.toString());
        return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
    }

    private ServerHttpRequest request(String path, String query, String authorization) {
        MockHttpServletRequest servlet = new MockHttpServletRequest("GET", path);
        servlet.setQueryString(query);
        if (authorization != null) servlet.addHeader(HttpHeaders.AUTHORIZATION, authorization);
        return new ServletServerHttpRequest(servlet);
    }
}
