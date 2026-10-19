package com.example.paymentsystem.webhook;

import com.example.paymentsystem.payment.PaymentProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, UUID> {
    Optional<WebhookEvent> findByProviderAndProviderEventId(PaymentProvider provider, String providerEventId);
    long countByStatus(WebhookStatus status);
}
