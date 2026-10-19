package pl.jakubtworek.chatsystem.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import pl.jakubtworek.chatsystem.message.MessageRepository;
import pl.jakubtworek.chatsystem.outbox.*;
import java.util.UUID;

@Component
public class MessageSearchProjectionHandler implements DomainEventHandler {
    private final ObjectMapper json; private final MessageRepository messages;
    private final MessageSearchProjectionService projection;
    public MessageSearchProjectionHandler(ObjectMapper json, MessageRepository messages,
                                          MessageSearchProjectionService projection) {
        this.json = json; this.messages = messages; this.projection = projection;
    }
    @Override public String consumerName() { return "chat.search-projection.v1"; }
    @Override public boolean supports(String type) {
        return EventTypes.MESSAGE_CREATED.equals(type) || EventTypes.MESSAGE_EDITED.equals(type)
                || EventTypes.MESSAGE_DELETED.equals(type);
    }
    @Override public void handle(OutboxEvent event) {
        try {
            UUID messageId = UUID.fromString(json.readTree(event.getPayloadJson()).get("messageId").asText());
            if (EventTypes.MESSAGE_DELETED.equals(event.getEventType())) projection.remove(messageId);
            else messages.findById(messageId).ifPresent(projection::project);
        } catch (Exception ex) { throw new IllegalStateException("Invalid search projection event", ex); }
    }
}
