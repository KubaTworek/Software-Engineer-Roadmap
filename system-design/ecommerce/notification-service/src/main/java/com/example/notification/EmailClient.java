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

    public void sendTransactionalEmail(String idempotencyKey, String template, String payloadJson) {
        // Adapter produkcyjny musi przekazać ten klucz providerowi albo zapisać
        // własny delivery ledger. Sam marker w bazie nie usuwa failure window
        // pomiędzy wysłaniem wiadomości a commitem transakcji workera.
        retry.run("email.send", () -> log.info(
                "MOCK_EMAIL_SENT idempotencyKey={}, template={}, payload={}",
                idempotencyKey, template, payloadJson));
    }
}
