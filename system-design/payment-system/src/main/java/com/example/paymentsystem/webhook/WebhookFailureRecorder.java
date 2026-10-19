package com.example.paymentsystem.webhook;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Component
class WebhookFailureRecorder {
    private final WebhookEventRepository repository;
    WebhookFailureRecorder(WebhookEventRepository repository) { this.repository = repository; }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void failed(UUID id, RuntimeException error) {
        repository.findById(id).orElseThrow().failed(error);
    }
}
