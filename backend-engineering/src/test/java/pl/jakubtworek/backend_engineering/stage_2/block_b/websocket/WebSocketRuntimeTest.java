package pl.jakubtworek.backend_engineering.stage_2.block_b.websocket;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebSocketRuntimeTest {

    @LocalServerPort
    private int port;

    @Autowired
    private RoadmapEventWebSocketHandler serverHandler;

    @Test
    void realWebSocketHandshakeResumesFromLastSeenSequence() throws Exception {
        serverHandler.publish("one");
        serverHandler.publish("two");
        CountDownLatch received = new CountDownLatch(1);
        AtomicReference<String> payload = new AtomicReference<>();

        WebSocketSession session = new StandardWebSocketClient().execute(
                new TextWebSocketHandler() {
                    @Override
                    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
                        payload.set(message.getPayload());
                        received.countDown();
                    }
                },
                new WebSocketHttpHeaders(),
                URI.create("ws://localhost:" + port + "/labs/events")).get(5, TimeUnit.SECONDS);

        try {
            session.sendMessage(new TextMessage("RESUME:1"));
            assertThat(received.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(payload.get()).isEqualTo("EVENT:2:two");
        } finally {
            session.close();
        }
    }
}
