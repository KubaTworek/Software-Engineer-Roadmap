package pl.jakubtworek.backend_engineering.stage_2.block_b.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import pl.jakubtworek.backend_engineering.stage_2.block_a.reference_flow.application.LiveProductUpdates;

/** Real Spring WebSocket adapter around the sequence/replay protocol. */
@Component
public final class RoadmapEventWebSocketHandler extends TextWebSocketHandler implements LiveProductUpdates {

    private final ResumableEventLog eventLog = new ResumableEventLog(100);

    @Override
    public void publish(String payload) {
        eventLog.append(payload);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        if (!message.getPayload().startsWith("RESUME:")) {
            session.sendMessage(new TextMessage("ERROR:expected RESUME:<lastSeenSequence>"));
            return;
        }
        long lastSeen = Long.parseLong(message.getPayload().substring("RESUME:".length()));
        try {
            for (StreamEvent event : eventLog.replayAfter(lastSeen)) {
                session.sendMessage(new TextMessage("EVENT:" + event.sequence() + ":" + event.payload()));
            }
        } catch (ResumeWindowExceededException exception) {
            session.sendMessage(new TextMessage("RESET_REQUIRED"));
            session.close();
        }
    }
}
