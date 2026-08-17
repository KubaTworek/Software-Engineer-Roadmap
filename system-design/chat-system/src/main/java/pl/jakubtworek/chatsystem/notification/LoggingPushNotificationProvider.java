package pl.jakubtworek.chatsystem.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingPushNotificationProvider implements PushNotificationProvider {
    private static final Logger log = LoggerFactory.getLogger(LoggingPushNotificationProvider.class);

    @Override
    public void send(PushPayload payload) {
        log.info("Push notification simulated: recipient={}, conversation={}, message={}, title={}, body={}",
                payload.recipientId(), payload.conversationId(), payload.messageId(), payload.title(), payload.body());
    }
}
