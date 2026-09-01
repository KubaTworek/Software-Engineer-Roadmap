package pl.jakubtworek.backend_engineering.stage_2.block_b.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class RoadmapWebSocketConfiguration implements WebSocketConfigurer {

    private final RoadmapEventWebSocketHandler handler;

    public RoadmapWebSocketConfiguration(RoadmapEventWebSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/labs/events");
    }
}
