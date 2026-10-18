package com.example.paymentsystem.reporting;

import com.example.paymentsystem.outbox.OutboxEventRepository;
import com.example.paymentsystem.payment.PaymentRepository;
import com.example.paymentsystem.payment.PaymentStatus;
import com.example.paymentsystem.payout.PayoutBatchRepository;
import com.example.paymentsystem.payout.PayoutStatus;
import com.example.paymentsystem.webhook.WebhookEventRepository;
import com.example.paymentsystem.webhook.WebhookStatus;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Metryki kolejek i stanów finansowych używają wyłącznie ograniczonych etykiet.
 * Identyfikatory płatności i merchantów nie mogą stać się tagami Prometheusa,
 * bo tworzyłyby nieograniczoną cardinality.
 */
@Component
public class PaymentOperationalMetrics implements MeterBinder {
    private final PaymentRepository payments;
    private final OutboxEventRepository outbox;
    private final WebhookEventRepository webhooks;
    private final PayoutBatchRepository payouts;

    public PaymentOperationalMetrics(PaymentRepository payments, OutboxEventRepository outbox,
                                     WebhookEventRepository webhooks, PayoutBatchRepository payouts) {
        this.payments = payments;
        this.outbox = outbox;
        this.webhooks = webhooks;
        this.payouts = payouts;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("payment.pending", payments, repo -> repo.countByStatus(PaymentStatus.PENDING)).register(registry);
        Gauge.builder("payment.unknown", payments, repo -> repo.countByStatus(PaymentStatus.UNKNOWN)).register(registry);
        Gauge.builder("payment.outbox.backlog", outbox,
                OutboxEventRepository::countByPublishedAtIsNullAndDeadLetteredAtIsNull).register(registry);
        Gauge.builder("payment.outbox.oldest.age.seconds", outbox, repo -> {
            Instant oldest = repo.oldestPendingCreatedAt();
            return oldest == null ? 0 : Math.max(0, Instant.now().getEpochSecond() - oldest.getEpochSecond());
        }).register(registry);
        Gauge.builder("payment.webhook.pending", webhooks,
                repo -> repo.countByStatus(WebhookStatus.RECEIVED)).register(registry);
        Gauge.builder("payment.payout.processing", payouts,
                repo -> repo.countByStatus(PayoutStatus.PROCESSING) + repo.countByStatus(PayoutStatus.UNKNOWN))
                .register(registry);
    }
}
