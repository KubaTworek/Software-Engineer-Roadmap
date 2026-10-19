package com.example.notification;

import com.example.notification.application.DeliveryAdmissionController;
import com.example.notification.application.NotificationStatusPolicy;
import com.example.notification.application.SchedulingPolicy;
import com.example.notification.domain.*;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class SystemPoliciesTest {
    private final NotificationStatusPolicy statuses = new NotificationStatusPolicy();

    @Test void aggregateDistinguishesPartialDeliveryFromTotalFailureAndUnknown() {
        NotificationJob delivered = job(); sent(delivered); delivered.markDelivered();
        NotificationJob failed = job(); failed.markProcessing(); failed.markFailed("rejected");
        assertThat(statuses.derive(List.of(delivered, failed))).isEqualTo(NotificationStatus.PARTIALLY_DELIVERED);

        NotificationJob unknown = job(); unknown.markProcessing(); unknown.markUnknown("timeout");
        assertThat(statuses.derive(List.of(delivered, unknown))).isEqualTo(NotificationStatus.UNKNOWN);
        assertThat(statuses.derive(List.of(failed))).isEqualTo(NotificationStatus.FAILED);
    }

    @Test void dstGapAndOverlapAreResolvedByExplicitPolicy() {
        SchedulingPolicy policy = new SchedulingPolicy(Clock.fixed(Instant.parse("2026-03-01T00:00:00Z"), ZoneOffset.UTC));
        ZoneId warsaw = ZoneId.of("Europe/Warsaw");
        Instant shiftedGap = policy.resolve(LocalDate.of(2026, 3, 29), LocalTime.of(2, 30), warsaw,
                SchedulingPolicy.OverlapPolicy.EARLIER_OFFSET);
        Instant earlier = policy.resolve(LocalDate.of(2026, 10, 25), LocalTime.of(2, 30), warsaw,
                SchedulingPolicy.OverlapPolicy.EARLIER_OFFSET);
        Instant later = policy.resolve(LocalDate.of(2026, 10, 25), LocalTime.of(2, 30), warsaw,
                SchedulingPolicy.OverlapPolicy.LATER_OFFSET);
        assertThat(shiftedGap).isEqualTo(Instant.parse("2026-03-29T01:00:00Z"));
        assertThat(Duration.between(earlier, later)).isEqualTo(Duration.ofHours(1));
    }

    @Test void admissionIsBoundedPerTenantAndReleasedDeterministically() {
        DeliveryAdmissionController admission = new DeliveryAdmissionController(2, 1);
        try (var first = admission.tryAcquire("tenant-a", "provider-a")) {
            assertThat(first).isNotNull();
            assertThat(admission.tryAcquire("tenant-a", "provider-b")).isNull();
        }
        assertThat(admission.tryAcquire("tenant-a", "provider-b")).isNotNull().satisfies(AutoCloseable::close);
    }

    private NotificationJob job() {
        return new NotificationJob(UUID.randomUUID(), UUID.randomUUID(), "t", "u", NotificationType.PAYMENT_FAILED,
                Channel.EMAIL, new ContactPoint("a@b.pl", null, null), Map.of(), 3, null, null, UUID.randomUUID(), 1);
    }
    private void sent(NotificationJob job) { job.markProcessing(); job.markSent(new ProviderSendResult("p", UUID.randomUUID().toString()), false); }
}
