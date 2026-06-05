package com.example.notification.infrastructure;

import com.example.notification.application.EmailSender;
import com.example.notification.domain.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void send(Notification notification) {
        if (notification.getRecipient().endsWith("@fail.local")) {
            throw new EmailSendingException("Simulated email provider failure");
        }

        log.info("Sending email notification: recipient={}, subject={}, message={}",
                notification.getRecipient(),
                notification.getSubject(),
                notification.getMessage());
    }
}
