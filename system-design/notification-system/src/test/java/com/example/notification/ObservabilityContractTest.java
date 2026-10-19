package com.example.notification;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Chroni przed dashboardem i alertami odwołującymi się do nieistniejących metryk. */
@SpringBootTest(properties = "notification.scheduling.enabled=false")
class ObservabilityContractTest {
    @Autowired MeterRegistry registry;

    @Test void everyOperationalMetricIsRegisteredAndReferencedByConfiguration() throws Exception {
        String dashboard = Files.readString(Path.of("ops/dashboard.json"));
        String alerts = Files.readString(Path.of("ops/prometheus-alerts.yml"));
        Set<String> expected = Set.of(
                "notification_oldest_ready_job_age_seconds", "notification_outbox_pending",
                "notification_dlq_size", "notification_time_to_send_seconds",
                "notification_provider_attempts_total", "notification_provider_unknown_total",
                "notification_jobs_sent_total", "notification_jobs_failed_total",
                "notification_jobs_delivered_total");
        for (String name : expected) {
            assertThat(registry.find(name).meters()).as("registered meter %s", name).isNotEmpty();
            assertThat(dashboard + alerts).as("dashboard or alert reference %s", name).contains(name);
        }
    }

    @Test void metricTagsNeverContainUnboundedBusinessIdentifiers() {
        registry.getMeters().forEach(meter -> assertThat(meter.getId().getTags())
                .noneMatch(tag -> Set.of("tenantId", "userId", "jobId", "notificationId", "providerMessageId")
                        .contains(tag.getKey())));
    }
}
