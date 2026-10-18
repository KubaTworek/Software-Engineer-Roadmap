package com.example.paymentsystem.webhook;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
class WebhookInboxWriter {
    private final WebhookEventRepository repository;

    WebhookInboxWriter(WebhookEventRepository repository) { this.repository = repository; }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    WebhookEvent insert(WebhookEvent event) { return repository.saveAndFlush(event); }
}
