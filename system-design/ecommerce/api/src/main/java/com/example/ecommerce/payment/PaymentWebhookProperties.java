package com.example.ecommerce.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Production boundary: sekret pochodzi z secret managera i podlega rotacji. */
@Component
@ConfigurationProperties("app.payments")
public class PaymentWebhookProperties {
    private String webhookSecret;
    private Duration maxWebhookAge = Duration.ofMinutes(5);

    public String getWebhookSecret() { return webhookSecret; }
    public void setWebhookSecret(String webhookSecret) { this.webhookSecret = webhookSecret; }
    public Duration getMaxWebhookAge() { return maxWebhookAge; }
    public void setMaxWebhookAge(Duration maxWebhookAge) { this.maxWebhookAge = maxWebhookAge; }
}
