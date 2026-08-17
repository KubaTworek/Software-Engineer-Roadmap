package com.example.notification;

import com.example.notification.integration.IntegrationRetryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class EmailClient {
    private static final Logger log = LoggerFactory.getLogger(EmailClient.class);
    private final IntegrationRetryService retry;

    public EmailClient(IntegrationRetryService retry) {
        this.retry = retry;
    }

    public void sendTransactionalEmail(String template, String payloadJson) {
        retry.run("email.send", () -> log.info("MOCK_EMAIL_SENT template={}, payload={}", template, payloadJson));
    }
}
